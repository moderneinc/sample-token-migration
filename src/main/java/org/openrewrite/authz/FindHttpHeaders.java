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

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

public class FindHttpHeaders extends Recipe {

    @Option(displayName = "Header name",
            description = "The name of the header to find.",
            example = "Authorization")
    private final String headerName;

    public FindHttpHeaders(String headerName) {
        this.headerName = headerName;
    }

    @Override
    public String getDisplayName() {
        return "Find HTTP authorization headers";
    }

    @Override
    public String getDescription() {
        return "Find various forms of authorization header.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher headerAdd = new MethodMatcher("org.springframework.http.HttpHeaders add(..)");
        return Preconditions.check(new UsesMethod<>(headerAdd), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (headerAdd.matches(method)) {
                    Expression key = method.getArguments().get(0);
                    if (key instanceof J.FieldAccess) {
                        J.FieldAccess fieldAccess = (J.FieldAccess) key;
                        if (fieldAccess.getSimpleName().equalsIgnoreCase(headerName)) {
                            return SearchResult.found(method);
                        }
                    } else if (key instanceof J.Identifier) {
                        J.Identifier identifier = (J.Identifier) key;
                        if (identifier.getSimpleName().equalsIgnoreCase(headerName)) {
                            return SearchResult.found(method);
                        }
                    } else if (key instanceof J.Literal) {
                        J.Literal literal = (J.Literal) key;
                        if (literal.getValue() instanceof String) {
                            String value = (String) literal.getValue();
                            if (value.equalsIgnoreCase(headerName)) {
                                return SearchResult.found(method);
                            }
                        }
                    }
                }
                return super.visitMethodInvocation(method, ctx);
            }
        });
    }
}
