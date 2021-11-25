/*
 * Copyright 2018 Roberto Leinardi.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.leinardi.pycharm.mypy;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.leinardi.pycharm.mypy.checker.Problem;
import com.leinardi.pycharm.mypy.checker.ScanFiles;
import com.leinardi.pycharm.mypy.checker.ScannableFile;
import com.leinardi.pycharm.mypy.exception.MypyPluginParseException;
import com.leinardi.pycharm.mypy.mpapi.MypyRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.leinardi.pycharm.mypy.MypyBundle.message;
import static com.leinardi.pycharm.mypy.util.Async.asyncResultOf;
import static com.leinardi.pycharm.mypy.util.Notifications.showException;
import static com.leinardi.pycharm.mypy.util.Notifications.showWarning;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;

public class MypyInspection extends LocalInspectionTool {

    private static final Logger LOG = Logger.getInstance(MypyInspection.class);
    private static final List<Problem> NO_PROBLEMS_FOUND = Collections.emptyList();
    private static final String ERROR_MESSAGE_INVALID_SYNTAX = "invalid syntax";

    private MypyPlugin plugin(final Project project) {
        final MypyPlugin mypyPlugin = project.getComponent(MypyPlugin.class);
        if (mypyPlugin == null) {
            throw new IllegalStateException("Couldn't get mypy plugin");
        }
        return mypyPlugin;
    }

    @Override
    public ProblemDescriptor[] checkFile(@NotNull final PsiFile psiFile,
                                         @NotNull final InspectionManager manager,
                                         final boolean isOnTheFly) {
        boolean unsaved = documentIsModifiedAndUnsaved(psiFile);
        long start = System.currentTimeMillis();
        LOG.debug("checkFile " + psiFile.getVirtualFile().getPresentableUrl()  // XXX may be null
                        + " isOnTheFly=" + isOnTheFly
                        + " modified=" + psiFile.getModificationStamp()
                        + " saved=" + !unsaved
                        + " thread=" + Thread.currentThread().getName()
        );

        if(!unsaved) {
            throw new RuntimeException("");
        }

        ProblemDescriptor[] results = asProblemDescriptors(asyncResultOf(() -> inspectFile(psiFile, manager),
                        NO_PROBLEMS_FOUND),
                manager);
        LOG.debug("checkFile " + psiFile.getName() + " DONE in " + (System.currentTimeMillis()-start) +" ms");
        return results;
    }

    // FIXME HACK copy-paste
    private Optional<VirtualFile> virtualFileOf(final PsiFile file) {
        return ofNullable(file.getVirtualFile());
    }

    // FIXME HACK copy-paste
    private boolean documentIsModifiedAndUnsaved(final PsiFile file) {
        final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        return virtualFileOf(file).filter(fileDocumentManager::isFileModified).map(fileDocumentManager::getDocument)
                .map(fileDocumentManager::isDocumentUnsaved).orElse(false);
    }

    @Nullable
    public List<Problem> inspectFile(@NotNull final PsiFile psiFile,
                                     @NotNull final InspectionManager manager) {

        final MypyPlugin plugin = plugin(manager.getProject());

        if (!MypyRunner.checkMypyAvailable(plugin.getProject())) {
            LOG.debug("Scan failed: Mypy not available.");
            return NO_PROBLEMS_FOUND;
        }

        final List<ScannableFile> scannableFiles = new ArrayList<>();
        try {
            scannableFiles.addAll(ScannableFile.createAndValidate(singletonList(psiFile), plugin));
            if (scannableFiles.isEmpty()) {
                return NO_PROBLEMS_FOUND;
            }
            ScanFiles scanFiles = new ScanFiles(plugin, Collections.singletonList(psiFile.getVirtualFile()));
            Map<PsiFile, List<Problem>> map = scanFiles.call();
            map.values().forEach(problems -> problems.removeIf(problem ->
                    problem.getMessage().equals(ERROR_MESSAGE_INVALID_SYNTAX)));
            if (map.isEmpty()) {
                return NO_PROBLEMS_FOUND;
            }
            return map.get(psiFile);

        } catch (ProcessCanceledException | AssertionError e) {
            LOG.debug("Process cancelled when scanning: " + psiFile.getName());
            return NO_PROBLEMS_FOUND;

        } catch (MypyPluginParseException e) {
            LOG.debug("Parse exception caught when scanning: " + psiFile.getName(), e);
            return NO_PROBLEMS_FOUND;

        } catch (Throwable e) {
            handlePluginException(e, psiFile, manager.getProject());
            return NO_PROBLEMS_FOUND;

        } finally {
            scannableFiles.forEach(ScannableFile::deleteIfRequired);
        }
    }

    private void handlePluginException(final Throwable e,
                                       final @NotNull PsiFile psiFile,
                                       final @NotNull Project project) {

        if (e.getCause() != null && e.getCause() instanceof ProcessCanceledException) {
            LOG.debug("Process cancelled when scanning: " + psiFile.getName());

        } else if (e.getCause() != null && e.getCause() instanceof IOException) {
            showWarning(project, message("mypy.file-io-failed"));

        } else {
            LOG.warn("Mypy threw an exception when scanning: " + psiFile.getName(), e);
            showException(project, e);
        }
    }

    @NotNull
    private ProblemDescriptor[] asProblemDescriptors(final List<Problem> results, final InspectionManager manager) {
        return ofNullable(results)
                .map(problems -> problems.stream()
                        .map(problem -> problem.toProblemDescriptor(manager))
                        .toArray(ProblemDescriptor[]::new))
                .orElse(ProblemDescriptor.EMPTY_ARRAY);
    }
}
