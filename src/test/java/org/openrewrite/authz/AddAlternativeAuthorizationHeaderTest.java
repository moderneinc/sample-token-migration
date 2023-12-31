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

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

public class AddAlternativeAuthorizationHeaderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddAlternativeAuthorizationHeader())
          .parser(JavaParser.fromJavaVersion()
            //language=java
            .dependsOn("""
              package org.springframework.http;
              public class HttpHeaders {
                    public static String AUTHORIZATION = "Authorization";
                    public static String EXPIRES = "Authorization";
                    
                    public void add(String key, String value) {}
              }
              """
            )
          );
    }

    @Test
    void addNewAuthzProvider() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            null,
            """
              public class IdaProvider {
                  public String getAuthorizationHeader() {
                      return "NEW_AUTHORIZATION";
                  }
              }
              """,
            //language=none
            spec -> spec.path("IdaProvider.java")
          ),
          //language=java
          java(
            """
              import org.springframework.http.HttpHeaders;
                            
              class Test {
                            
                  void test(HttpHeaders headers) {
                      headers.add(HttpHeaders.AUTHORIZATION, "Bearer token");
                  }
              }
              """,
            """
              import org.springframework.http.HttpHeaders;

              class Test {
                  
                  IdaProvider idaProvider;

                  void test(HttpHeaders headers) {
                      headers.add(HttpHeaders.AUTHORIZATION, "Bearer token");
                      headers.add("NEW_AUTHORIZATION", idaProvider.getAuthorizationHeader());
                  }
              }
              """
          )
        );
    }

    @Test
    void useExistingProvider() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package com.example;
                            
              public class IdaProvider {
                  public String getAuthorizationHeader() {
                      return "NEW_AUTHORIZATION";
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import org.springframework.http.HttpHeaders;

              class Test {
                            
                  void test(HttpHeaders headers) {
                      headers.add(HttpHeaders.AUTHORIZATION, "Bearer token");
                  }
              }
              """,
            """
              import com.example.IdaProvider;
              import org.springframework.http.HttpHeaders;

              class Test {
                  
                  IdaProvider idaProvider;

                  void test(HttpHeaders headers) {
                      headers.add(HttpHeaders.AUTHORIZATION, "Bearer token");
                      headers.add("NEW_AUTHORIZATION", idaProvider.getAuthorizationHeader());
                  }
              }
              """
          )
        );
    }
}
