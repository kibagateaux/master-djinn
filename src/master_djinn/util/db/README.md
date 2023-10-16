## Best Practices
- Neo4j cant handle kebab case well so keys should be snake_case
- Always use tx not sessions. Allows multiple read/writes to be run in a single ACID instance (this seems to be causing issues with grapqhl resolvers where tx ends so we cant read results)
- prefer label over properties for things we will want to run lots of queries for

there are a few ways to manage transactions and sessions documented in 
![./db-tx-patterns-w-lacinia.png]
- ensure that database for your environment has all APOC procedures loaded by adding this field in your db config file or add to docker with
```
docker run \
    -p 7474:7474 -p 7687:7687 \
    --name neo4j-apoc \
    -e apoc.export.file.enabled=true \
    -e apoc.import.file.enabled=true \
    -e apoc.import.file.use_neo4j_config=true \
    -e NEO4J_PLUGINS=\[\"apoc\"\] \
    neo4j:5.14.0
```
- UUID hierarchichal namespaces "This capability can be used to represent uniqueness of a sequence of computations in, for example, a transaction system such as the one used in the graph-object database system" - [clj-uuid](https://github.com/danlentz/clj-uuid#hierarchical-namespace) Interesting way to generate UUIDs for consequential actions based on initial action UUID and transmuter function id or for habit stacking runes.

## Example Queries
#### Get all Players, their actions, and where we imported data from 
```cypher
MATCH (p)-[rp:DID]-(a:Action)-[rd:ATTESTS]-(d:DataProvider) RETURN p,rp,a,d,rd
```


## Database Design
Node Labels:

Pros:

1. Performance: Labels allow Neo4j to quickly filter nodes during query execution, which can significantly improve performance for large datasets.

2. Schema Information: Labels can provide a form of schema information, making the graph data model easier to understand.

3. Indexing: You can create indexes on labels, which can significantly improve the performance of queries that filter on labels.

Cons:

1. Limited Flexibility: Labels are less flexible than properties. A node can have multiple labels, but this is less dynamic than having a property which can hold a range of different values.

2. Query Complexity: If you use many labels, it can make your Cypher queries more complex, as you need to specify the correct label in each query.

Node Properties:

Pros:

1. Flexibility: Properties are more flexible than labels. A property can hold a range of different values, including strings, numbers, and booleans.

2. Complex Data Models: Properties allow you to model more complex data scenarios, as you can have many properties on a single node.

Cons:

1. Performance: Filtering nodes based on properties can be slower than filtering based on labels, especially for large datasets.

2. No Indexing: Neo4j does not support indexing on properties. If you need to frequently filter nodes based on a property, you might need to use a label instead.

In general, whether you should use labels or properties depends on your specific use case. If you need to frequently filter nodes based on a certain attribute, and this attribute has a limited number of values, using a label might be more efficient. On the other hand, if you need to store a range of different values for an attribute, using a property might be more appropriate.
