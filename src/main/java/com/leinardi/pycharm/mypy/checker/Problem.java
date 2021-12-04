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

package com.leinardi.pycharm.mypy.checker;

import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.leinardi.pycharm.mypy.MypyBundle;
import com.leinardi.pycharm.mypy.mpapi.SeverityLevel;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.jetbrains.annotations.NotNull;

public class Problem {
    private final PsiElement target;
    private final SeverityLevel severityLevel;
    private final int line;
    private final int column;
    private final String message;
    private final boolean afterEndOfLine;
    private final boolean suppressErrors;

    public Problem(final PsiElement target,
                   final String message,
                   final SeverityLevel severityLevel,
                   final int line,
                   final int column,
                   final boolean afterEndOfLine,
                   final boolean suppressErrors) {
        this.target = target;
        this.message = message;
        this.severityLevel = severityLevel;
        this.line = line;
        this.column = column;
        this.afterEndOfLine = afterEndOfLine;
        this.suppressErrors = suppressErrors;
    }

    public void createAnnotation(@NotNull AnnotationHolder holder) {
        String message = MypyBundle.message("inspection.message", getMessage());
        AnnotationBuilder annotation = holder
                .newAnnotation(getHighlightSeverity(), message)
                .range(target.getTextRange());
        if (isAfterEndOfLine()) {
            annotation = annotation.afterEndOfLine();
        }
        annotation.create();
    }

    public SeverityLevel getSeverityLevel() {
        return severityLevel;
    }

    @NotNull
    public HighlightSeverity getHighlightSeverity() {
        switch (severityLevel) {
            case ERROR:
                return HighlightSeverity.ERROR;
            case WARNING:
            case NOTE: // WEAK_WARNING can be a bit difficult to see, use WARNING instead
                return HighlightSeverity.WARNING;
            default:
                assert false : "Unhandled SeverityLevel: " + severityLevel;
        }
        return null;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getMessage() {
        return message;
    }

    public boolean isAfterEndOfLine() {
        return afterEndOfLine;
    }

    public boolean isSuppressErrors() {
        return suppressErrors;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("target", target)
                .append("message", message)
                .append("severityLevel", severityLevel)
                .append("line", line)
                .append("column", column)
                .append("afterEndOfLine", afterEndOfLine)
                .append("suppressErrors", suppressErrors)
                .toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(target)
                .append(message)
                .append(severityLevel)
                .append(line)
                .append(column)
                .append(afterEndOfLine)
                .append(suppressErrors)
                .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Problem)) {
            return false;
        }
        Problem rhs = ((Problem) other);
        return new EqualsBuilder()
                .append(target, rhs.target)
                .append(message, rhs.message)
                .append(severityLevel, rhs.severityLevel)
                .append(line, rhs.line)
                .append(column, rhs.column)
                .append(afterEndOfLine, rhs.afterEndOfLine)
                .append(suppressErrors, rhs.suppressErrors)
                .isEquals();
    }
}
