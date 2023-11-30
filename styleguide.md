## Variables
1. Snake case for any "dirty" variables that are input or output to external systems, mainly user data from GraphQL or submitted to DB (Neo4j doesnt handle kebab-case well). Kebab-case for any "safe" data that has been generated/processed by our clojure code already.

