# Setup
1. Install APOC plugin
1. Instal vector embed plugin
1. Run constraints query from each db/*.clj file
1. Create db.core/MASTER_DJINN_DATA_PROVIDER initial voucher nodes with util.crypto card ids
1. 




## Deduping nodes 
```cypher
// Step 1: Identify duplicate Avatar nodes
MATCH (a:Avatar)
WITH a.id AS id, COLLECT(a) AS avatars
WHERE SIZE(avatars) > 1

// Step 2: Order avatars by number of relationships (descending) to keep the most connected one
WITH id, avatars, [a IN avatars | SIZE([(a)-[]-() | 1])] AS relCounts
ORDER BY id, relCounts DESC

// Step 3: Separate the avatar to keep (first one) from the ones to delete
WITH id, avatars[0] AS keepAvatar, avatars[1..] AS deleteAvatars

// Step 4: Move all relationships from avatars to be deleted to the avatar to keep
UNWIND deleteAvatars AS deleteAvatar
OPTIONAL MATCH (deleteAvatar)-[r]-()
WITH id, keepAvatar, deleteAvatar, r
CALL apoc.refactor.to(r, keepAvatar)
YIELD input, output
WITH DISTINCT id, keepAvatar, deleteAvatar

// Step 5: Delete the duplicate avatars
DETACH DELETE deleteAvatar

// Return the results
RETURN id, keepAvatar, COUNT(deleteAvatar) AS deletedCount
```