# Gatekeep-Role-DiscordBot

## Intro

Discord bot to help control permissions for a role that is automatically added by a Discord integration.

Typically most servers use a 'gatekeep' role to flag people with access to the rest of the server, once they have been vetted and/or accepted some rules.

Wanting to tie some channels to particular integration granted roles complicates this, as it is not possible to describe a requirement of multiple roles to view any given channel.

This bot helps circumvent that issue, by deferring access permissions via an additional 'access role', that the bot assigns only to members who acquire both the integration role and the gatekeep role.

The canonical example is providing special subscriber only channels to twitch subscribers, here we can set up an access role (call it Subscriber Access) and set the permissions for the subscriber only channels to require that access role.

Someone can gain the integration role, join the server and still only see the landing channel.

Later once they are given access with the gatekeep role, the bot will automatically give them the access role and revoke it if either role is removed.

## Build

Clone repo

	git clone https://github.com/Psotle/gatekeep-role-discordbot.git

Build with gradle

	`./gradlew.bat shadowJar`

Output in
	
	build/libs/gatekeep-role-discordbot-0.2.0.jar

## Server configuration

1. Create the access role.
2. Remove the integration role from the permissions list for the existing channels.
3. Add the access role to the permissions list for the channels in it's place.
4. Manually add the access role to users who already have the integration role.

## Discord Bot Config	

Setup Discord Bot:

1. Navigate to https://discord.com/developers/applications/
2. Click create 'New Application', give it a name.
3. Select 'Bot' from the left hand menu -> Click 'Add Bot'
4. Give it a name and an icon and fill out any personality details you like.
5. Click 'Reset Token' and note the token to use for the BOT_TOKEN env variable.
6. Enable 'Server Members Intent'
7. Select 'OAuth2' from the main menu -> 'URL Generator'
8. Select 'Bot' and 'applications.command' from the Scopes section.
9. Select 'Manage Roles' from Bot Permissions.
10. Copy the Generated URL and put it into a new tab.
11. Invite the Bot to your Server.
12. Start the bot

## Setup Bot

Bot uses the following environment variables to pick up configuration:

	BOT_TOKEN => Discord Auth Token for bot.
	Required: true


	INTEGRATION_ROLE => Role added by Integration
	Default: 'Twitch Subscriber'


	ACCESS_ROLE => Role actual permissions will hang on
	Default: 'Subscriber Access'


	GATEKEEP_ROLE => Role added by moderators once user has passed gatekeeping
	Default: 'Follower'

## Run the Bot

To have the access role added to permitted users, you need to keep the bot running!

### Native

Start the application with JDK:

	export BOT_TOKEN=<token>
	export INTEGRATION_ROLE="twitch subscriber"
	export ACCESS_ROLE="access role
	export GATEKEEP_ROLE="follower"

	java -jar gatekeep-role-discordbot-0.2.0.jar

### Docker

Run up with Docker:

	docker run psotle/gatekeep-role-discordbot:latest -e BOT_TOKEN=<DiscordBotToken> -e INTEGRATION_ROLE=TargetRole\ Name -e ACCESS_ROLE=Access\ Role\ Name -e GATEKEEP_ROLE=gatekeep\ role\ name
