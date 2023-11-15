## Sample token migration

Installation:

```
mod config recipes jar install org.openrewrite.recipe:sample-token-migration:0.2.0-SNAPSHOT
```

Running:

```
mod run <PATH> --recipe FindHttpHeaders -PheaderName=Authorization
mod run <PATH> --recipe AddAlternativeAuthorizationHeader
```
