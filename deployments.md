## Step 1 - Build
```
source .env && 
docker build . -t djinn:test --build-arg ACTIVITYDB_URI=${ACTIVITYDB_URI} --build-arg ACTIVITYDB_USER=${ACTIVITYDB_USER} --build-arg ACTIVITYDB_PW=${ACTIVITYDB_PW} --build-arg IDENTITYDB_URI=${IDENTITYDB_URI} --build-arg IDENTITYDB_USER=${IDENTITYDB_USER} --build-arg IDENTITYDB_PW=${IDENTITYDB_PW} --build-arg SPOTIFY_CLIENT_ID=${SPOTIFY_CLIENT_ID} --build-arg SPOTIFY_CLIENT_SECRET=${SPOTIFY_CLIENT_SECRET}
```

```
docker run djinn:test -p 8888:8888 -p 8000:8000
```

## Step 2 - Update
`docker push kibagateaux/master-djinn:test-0-0-1`

## Step 3 - restart server
`ssh into cloud`
`docker run - port etc` 