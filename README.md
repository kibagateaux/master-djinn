# master-djinn

Djinn are the elite of the jinn world. They are the most powerful.
"Dj" vs "j" follows arabic vernacular of emphasizing letters influencing meaning of words


## Project Setup

Portal/ - anything that allows interactions with outside world with the game. aka API 
Incantations/ - Spells for different user actions
   1. Conjure - Initiate data ingestion from this server from a given source
   2. Transmute - Normalize data sent by user requests or conjure requests to game values for database
   3. Evoke - Player abilities with side effects in/outside of our system e.g. saving to database
   3. Divination - Ways of reading player data and augmenting avatar images based on activity
   3. Rituals - Player habits thats are run on a schedule e.g. Did they complete their quests this week?
   3. Illusion - ZK stuffs TBD


#### Evnironment Variables
Currently accept 2 ways to add environment variables to the project for repl and uberjar for local and production builds
1. resources/env.edn (local support)
2. export MY_ENV_VAR=myvar (local & prod support)
*Important* -- `environ` will not pick up configuration settings from the project.clj when called from a compiled uberjar. So for any compiled code you produce with lein uberjar, you will want to set your configuration values via shell environment and/or system properties. 

## Clojure Cookbook
- [Destructuring](https://gist.github.com/john2x/e1dca953548bfdfb9844)
## Getting Started


1. Start the application: `lein repl`
2. ` (require '[master-djinn.server :as server])`
3. `(server/-main)` to run server
2. Go to [localhost:8888/ide](http://localhost:8888/ide/) for GraphiQL playground. Regular HTTP server runs at [localhost:8000](http://localhost:8000/) but only used for web2 integrations at the moment, most everything gets routed through GraphQL queries and mutations.
3. Load functions in as needed e.g. 
4. Run tests with `lein test`. Read the tests at test/master_djinn/service_test.clj.


## Configuration

To configure logging see config/logback.xml. By default, the app logs to stdout and logs/.
To learn more about configuring Logback, read its [documentation](http://logback.qos.ch/documentation.html).

use `ngrok http 8888` to expose local server to internet for OAuth callback testing and such. MUST update OAuth config on provider with new ngrok url. 

## Developing your service

1. Start a new REPL: `lein repl`
2. Start your service in dev-mode: `(def dev-serv (run-dev))`
3. Connect your editor to the running REPL session.
   Re-evaluated code will be seen immediately in the service.

### [Docker](https://www.docker.com/) container support

1. Configure your service to accept incoming connections (edit service.clj and add  ::http/host "0.0.0.0" )
2. Build an uberjar of your service: `lein uberjar`
3. Build a Docker image: `sudo docker build -t master-djinn .`
4. Run your Docker image: `docker run -p 8000:8000 master-djinn`

### [OSv](http://osv.io/) unikernel support with [Capstan](http://osv.io/capstan/)

1. Build and run your image: `capstan run -f "8080:8080"`

Once the image it built, it's cached.  To delete the image and build a new one:

1. `capstan rmi master-djinn; capstan build`


## Links
* [Other Pedestal examples](http://pedestal.io/samples)

## Project TODOs
- migrate to deps.edn?
- Verify api requests via ETH wallet signatures (address that signed and the query they want executed) as an lacinia interceptor
