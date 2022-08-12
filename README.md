# Gatekeep-Role-DiscordBot

## Intro

Discord bot to help control permissions for a role that is automatically added by an integration.

Typically most servers use a 'gatekeep' role to flag people with access to the rest of the server after they have been vetted or accepted some rules. 

Wanting to tie some channels to particular integration granted roles complicates this as it is not possible to describe a requirement of multiple roles.

This bot help circumvent that issue by deferring access permissions onto an 'access role' the bot assigns only to members who acquire both the integration role and the gatekeep role.

The canonical example is having the twitch integration and special subscriber only channels: here we can set up an access role (call it Subscriber Access) and have the permissions for the subscriber only channels on it.

Someone can gain the integration role, join the server and still only see the landing channel, be given access and the bot will automatically give them further access to the subscriber only channels and revoke them as their roles are removed.

## Discord Bot Config	

Setup Discord Bot:

Navigate to https://discord.com/developers/applications/

Click create 'New Application', give it a name.

Select 'Bot' from the left hand menu -> Click 'Add Bot'

Give it a name and an icon and fill out any personality details you like.

Click 'Reset Token' and note the token to use for the BOT_TOKEN env variable.

Enable 'Server Members Intent'

Select 'OAuth2' from the main menu -> 'URL Generator'

Select 'Bot' and 'applications.command' from the Scopes section.

Select 'Manage Roles' from Bot Permissions.

Copy the Generated URL and put it into a new tab.

Invite the Bot to your Server.

## Setup Bot

Bot uses environment variables to pick up configuration:

	BOT_TOKEN => Discord Auth Token for bot.
	Required: true

	INTEGRATION_ROLE => Role added by Integration
	Default: 'Twitch Subscriber'

	ACCESS_ROLE => Role actual permissions will hang on
	Default: 'Subscriber Access'

	GATEKEEP_ROLE => Role added by moderators once user has passed gatekeeping
	Default: 'Follower'

## Run the Bot

### Native

Start the application with JDK:

	export BOT_TOKEN=<token>
	export INTEGRATION_ROLE="twitch subscriber"
	export ACCESS_ROLE="access role
	export GATEKEEP_ROLE="follower"

	java -jar gatekeep-role-discordbot.jar

### Docker

Run with Docker:

	docker run psotle/gatekeep-role-discordbot:latest -e BOT_TOKEN=<DiscordBotToken> -e INTEGRATION_ROLE=TargetRole\ Name -e ACCESS_ROLE=Access\ Role\ Name -e GATEKEEP_ROLE=gatekeep\ role\ name

