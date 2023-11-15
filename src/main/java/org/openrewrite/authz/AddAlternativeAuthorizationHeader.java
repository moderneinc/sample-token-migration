/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.authz;

import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindFieldsOfType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public class AddAlternativeAuthorizationHeader extends ScanningRecipe<AtomicReference<JavaType.Class>> {

    @Override
    public String getDisplayName() {
        return "Add alternative authorization header";
    }

    @Override
    public String getDescription() {
        return "The new authorization header allows the downstream service to choose which header to validate.";
    }

    @Override
    public AtomicReference<JavaType.Class> getInitialValue(ExecutionContext ctx) {
        return new AtomicReference<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicReference<JavaType.Class> acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                JavaType.Class fqn = TypeUtils.asClass(classDecl.getType());
                if (fqn != null && fqn.getClassName().equals("IdaProvider")) {
                    acc.set(fqn);
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }
        };
    }

    @SuppressWarnings("ConcatenationWithEmptyString")
    @Override
    public Collection<? extends SourceFile> generate(AtomicReference<JavaType.Class> acc, ExecutionContext ctx) {
        if (acc.get() == null) {
            //language=java
            return JavaParser.fromJavaVersion().build().parse(
                            "" +
                            "public class IdaProvider {\n" +
                            "    public String getAuthorizationHeader() {\n" +
                            "        return \"NEW_AUTHORIZATION\";\n" +
                            "    }\n" +
                            "}"
                    )
                    .peek(cu -> acc.set(TypeUtils.asClass(((J.CompilationUnit) cu).getClasses().get(0).getType())))
                    .collect(toList());
        }
        return emptyList();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicReference<JavaType.Class> acc) {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = classDecl;
                boolean hasAuthorization = hasAuthorizationHeader(classDecl, ctx);
                if (hasAuthorization) {
                    c = maybeAutowireProvider(classDecl, c);
                }

                return super.visitClassDeclaration(c, ctx);
            }

            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                if (getCursor().getParentTreeCursor().getValue() instanceof J.MethodDeclaration) {
                    for (Statement statement : block.getStatements()) {
                        if (hasAuthorizationHeader(statement, ctx) && !hasNewAuthorizationHeader(ctx)) {
                            String idaProviderName = getCursor().getNearestMessage("idaProviderName");
                            if (idaProviderName != null) {
                                return JavaTemplate.builder("headers.add(\"NEW_AUTHORIZATION\", #{}.getAuthorizationHeader());")
                                        .contextSensitive()
                                        .build()
                                        .apply(
                                                getCursor(),
                                                statement.getCoordinates().after(),
                                                idaProviderName
                                        );
                            }
                        }
                    }
                }
                return b;
            }

            private boolean hasAuthorizationHeader(J j, ExecutionContext ctx) {
                return new FindHttpHeaders("Authorization").getVisitor().visit(j, ctx, getCursor().getParentOrThrow()) != j;
            }

            private boolean hasNewAuthorizationHeader(ExecutionContext ctx) {
                return new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                        if ("NEW_AUTHORIZATION".equals(literal.getValue())) {
                            return SearchResult.found(literal);
                        }
                        return super.visitLiteral(literal, ctx);
                    }
                }.visit(getCursor().getValue(), ctx, getCursor().getParentOrThrow()) != getCursor().getValue();
            }

            private J.ClassDeclaration maybeAutowireProvider(J.ClassDeclaration classDecl, J.ClassDeclaration c) {
                Set<J.VariableDeclarations> idaProvider = FindFieldsOfType.find(classDecl, acc.get().getFullyQualifiedName());
                String fieldName;
                if (idaProvider.isEmpty()) {
                    maybeAddImport(acc.get());
                    fieldName = "idaProvider";

                    // add the new field
                    c = JavaTemplate.builder("#{} idaProvider")
                            .build()
                            .apply(getCursor(), classDecl.getBody().getCoordinates().firstStatement(),
                                    acc.get().getClassName());
                    c = c.withBody(c.getBody().withStatements(ListUtils.mapFirst(c.getBody().getStatements(),
                            provider -> ((J.VariableDeclarations) provider).withType(acc.get()))));

                    // not strictly necessary in this case, but shows how to update the cursor
                    // for subsequent operations on the same tree
                    updateCursor(c);
                } else {
                    fieldName = idaProvider.iterator().next().getVariables().get(0).getSimpleName();
                }
                getCursor().putMessage("idaProviderName", fieldName);
                return c;
            }
        };
    }
}
