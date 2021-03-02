/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.properties;

import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.typeconversion.UnsupportedNotationException;

import java.io.File;
import java.util.Map;

import static org.gradle.internal.reflect.validation.Severity.ERROR;

public enum ValidationActions implements ValidationAction {
    NO_OP("file collection") {
        @Override
        public void doValidate(String propertyName, Object value, TaskValidationContext context) {
        }
    },
    INPUT_FILE_VALIDATOR("file") {
        @Override
        public void doValidate(String propertyName, Object value, TaskValidationContext context) {
            File file = toFile(context, value);
            if (!file.exists()) {
                reportMissingInput(context, "File", propertyName, file);
            } else if (!file.isFile()) {
                reportUnexpectedInputKind(context, "File", propertyName, file);
            }
        }
    },
    INPUT_DIRECTORY_VALIDATOR("directory") {
        @Override
        public void doValidate(String propertyName, Object value, TaskValidationContext context) {
            File directory = toDirectory(context, value);
            if (!directory.exists()) {
                reportMissingInput(context, "Directory", propertyName, directory);
            } else if (!directory.isDirectory()) {
                reportUnexpectedInputKind(context, "Directory", propertyName, directory);
            }
        }
    },
    OUTPUT_DIRECTORY_VALIDATOR("file") {
        @Override
        public void doValidate(String propertyName, Object value, TaskValidationContext context) {
            File directory = toFile(context, value);
            validateNotInReservedFileSystemLocation(context, directory);
            if (directory.exists()) {
                if (!directory.isDirectory()) {
                    reportCannotWriteToDirectory(propertyName, context, directory, "'" + directory + "' is not a directory");
                }
            } else {
                for (File candidate = directory.getParentFile(); candidate != null && !candidate.isDirectory(); candidate = candidate.getParentFile()) {
                    if (candidate.exists() && !candidate.isDirectory()) {
                        reportCannotWriteToDirectory(propertyName, context, candidate, "'" + directory + "' ancestor '" + candidate + "' is not a directory");
                        return;
                    }
                }
            }
        }
    },
    OUTPUT_DIRECTORIES_VALIDATOR("file collection") {
        @Override
        public void doValidate(String propertyName, Object values, TaskValidationContext context) {
            for (File directory : toFiles(context, values)) {
                OUTPUT_DIRECTORY_VALIDATOR.validate(propertyName, directory, context);
            }
        }
    },
    OUTPUT_FILE_VALIDATOR("file") {
        @Override
        public void doValidate(String propertyName, Object value, TaskValidationContext context) {
            File file = toFile(context, value);
            validateNotInReservedFileSystemLocation(context, file);
            if (file.exists()) {
                if (file.isDirectory()) {
                    reportCannotWriteToFile(propertyName, context, "'" + file + "' is not a file");
                }
                // else, assume we can write to anything that exists and is not a directory
            } else {
                for (File candidate = file.getParentFile(); candidate != null && !candidate.isDirectory(); candidate = candidate.getParentFile()) {
                    if (candidate.exists() && !candidate.isDirectory()) {
                        reportCannotWriteToFile(propertyName, context, "'" + file + "' ancestor '" + candidate + "' is not a directory");
                        break;
                    }
                }
            }
        }
    },
    OUTPUT_FILES_VALIDATOR("file collection") {
        @Override
        public void doValidate(String propertyName, Object values, TaskValidationContext context) {
            for (File file : toFiles(context, values)) {
                OUTPUT_FILE_VALIDATOR.validate(propertyName, file, context);
            }
        }
    };

    private static void reportMissingInput(TaskValidationContext context, String kind, String propertyName, File input) {
        context.visitPropertyProblem(problem -> {
            String lowerKind = kind.toLowerCase();
            problem.withId(ValidationProblemId.INPUT_DOES_NOT_EXIST)
                .forProperty(propertyName)
                .reportAs(ERROR)
                .withDescription(() -> lowerKind + " '" + input + "' doesn't exist")
                .happensBecause("An input is missing")
                .addPossibleSolution(() -> "Make sure the " + lowerKind + " exists before the task is called")
                .addPossibleSolution(() -> "Make sure that the task which produces the " + lowerKind + " is declared as an input")
                .documentedAt("validation_problems", "input_does_not_exist");
        });
    }

    private static void reportUnexpectedInputKind(TaskValidationContext context, String kind, String propertyName, File input) {
        context.visitPropertyProblem(problem -> {
            String lowerKind = kind.toLowerCase();
            problem.withId(ValidationProblemId.UNEXPECTED_INPUT_TYPE)
                .forProperty(propertyName)
                .reportAs(ERROR)
                .withDescription(() -> lowerKind + " '" + input + "' is not a " + lowerKind)
                .happensBecause(() -> "Expected an input to be a " + kind.toLowerCase() + " but it was a " + actualKindOf(input))
                .addPossibleSolution(() -> "Use a " + lowerKind + " as an input")
                .addPossibleSolution(() -> "Declare the input as a " + actualKindOf(input) + " instead")
                .documentedAt("validation_problems", "unexpected_input_type");
        });
    }

    private static void reportCannotWriteToDirectory(String propertyName, TaskValidationContext context, File directory, String cause) {
        context.visitPropertyProblem(problem ->
            problem.withId(ValidationProblemId.CANNOT_WRITE_OUTPUT)
                .reportAs(ERROR)
                .forProperty(propertyName)
                .withDescription(() -> "is not writable because " + cause)
                .happensBecause(() -> "Expected '" + directory + "' to be a directory but it's a " + actualKindOf(directory))
                .addPossibleSolution("Make sure that the '" + propertyName + "' is configured to a directory")
                .documentedAt("validation_problems", "cannot_write_output")
        );
    }


    private static void reportCannotWriteToFile(String propertyName, TaskValidationContext context, String cause) {
        context.visitPropertyProblem(problem ->
            problem.withId(ValidationProblemId.CANNOT_WRITE_OUTPUT)
                .reportAs(ERROR)
                .forProperty(propertyName)
                .withDescription(() -> "is not writable because " + cause)
                .happensBecause(() -> "Cannot write a file to a location pointing at a directory")
                .addPossibleSolution(() -> "Configure '" + propertyName + "' to point to a file, not a directory")
                .documentedAt("validation_problems", "cannot_write_output")
        );
    }


    private static String actualKindOf(File input) {
        if (input.isFile()) {
            return "file";
        }
        if (input.isDirectory()) {
            return "directory";
        }
        return "unexpected file type";
    }

    private static void validateNotInReservedFileSystemLocation(TaskValidationContext context, File location) {
        if (context.isInReservedFileSystemLocation(location)) {
            context.visitPropertyProblem(ERROR, String.format("The output %s must not be in a reserved location", location));
        }
    }

    private final String targetType;

    ValidationActions(String targetType) {
        this.targetType = targetType;
    }

    protected abstract void doValidate(String propertyName, Object value, TaskValidationContext context);

    @Override
    public void validate(String propertyName, Object value, TaskValidationContext context) {
        try {
            doValidate(propertyName, value, context);
        } catch (UnsupportedNotationException ignored) {
            context.visitPropertyProblem(ERROR, String.format("Value '%s' specified for property '%s' cannot be converted to a %s", value, propertyName, targetType));
        }
    }

    private static File toDirectory(TaskValidationContext context, Object value) {
        if (value instanceof ConfigurableFileTree) {
            return ((ConfigurableFileTree) value).getDir();
        }
        return toFile(context, value);
    }

    private static File toFile(TaskValidationContext context, Object value) {
        return context.getFileOperations().file(value);
    }

    private static Iterable<? extends File> toFiles(TaskValidationContext context, Object value) {
        if (value instanceof Map) {
            return toFiles(context, ((Map) value).values());
        } else if (value instanceof FileCollection) {
            return (FileCollection) value;
        } else {
            return context.getFileOperations().immutableFiles(value);
        }
    }
}
