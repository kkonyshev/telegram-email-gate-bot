## Start from console

- Build binaries: `sbt clean stage`
- Set environments, see: `application.confs`
- Run the bot: `./target/universal/stage/bin/telegram-email-gate-bot`


## Build docker image

- Build image: `sbt docker:publishLocal`
- Run container: ` docker run --rm telegram-email-gate-bot:0.1`

#### Links
- [how to run sbt app in docker](https://medium.com/jeroen-rosenberg/lightweight-docker-containers-for-scala-apps-11b99cf1a666)
- [Cache library](https://cb372.github.io/scalacache/) 

## Usage

- Share a location with the bot
