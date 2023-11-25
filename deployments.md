## Step 1 - Build
```
source .env && 
docker build . -t djinn:test
```

```
docker-compose up
```

## Step 2 - Update
`docker push kibagateaux/master-djinn:test-0-0-1`

## Step 3 - Deploying Server
Ensure you have copied `docker-compose.yml` and .`env` to your server first
```
echo alias docker-compose="'"'docker run --rm \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v "$PWD:$PWD" \
    -w="$PWD" \
    docker/compose'"'" >> ~/.bashrc

docker pull docker/compose

source ~/.bashrc

docker-compose up
```
