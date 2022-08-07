package io.github.psotle.gatekeeprolediscordbot;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.server.ServerUpdater;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.role.UserRoleEvent;

/**
 * Very simple bot to originally designed to allow use of the Twitch Discord Integration with a
 * layer of security.
 *
 * <p>Using the role integrations assigns to users to hang off visibility and other permission is
 * dangerous.
 *
 * <p>This bot utilises another 'access' role to for those permissions and then adds that role to
 * users when they have the integration role assigned to them and they have another 'gatekeeping'
 * role assigned to them by being permitted by manual vetting into the server.
 *
 * <p>While the bot is offline, moderators will have to step in to reconcile things, as there's no
 * way to access the current list of users and their roles without using an intent that requires
 * OAuth which is a real pain to setup. Covering small blips in high uptime shouldn't be much of an
 * issue.
 *
 * @author Psotle
 */
public class Main {

  private static final String BOT_TOKEN_ENV = "BOT_TOKEN";

  private static final String TARGET_ROLE_ENV = "TWITCH_ROLE";
  private static final String ACCESS_ROLE_ENV = "ACCESS_ROLE";
  private static final String GATEKEEP_ROLE_ENV = "GATEKEEP_ROLE";

  private static final String DEFAULT_TARGET_ROLE = "twitch subscriber";
  private static final String DEFAULT_ACCESS_ROLE = "subscriber access";
  private static final String DEFAULT_GATEKEEP_ROLE = "follower";

  private Logger logger = LogManager.getLogger();

  public void mainTask() {
    // Handle ENV vars
    final String token =
        Optional.ofNullable(System.getenv(BOT_TOKEN_ENV))
            .orElseThrow(() -> new RuntimeException("No BOT_TOKEN set"));

    final String targetRoleName =
        Optional.ofNullable(System.getenv(TARGET_ROLE_ENV)).orElse(DEFAULT_TARGET_ROLE);

    final String accessRoleName =
        Optional.ofNullable(System.getenv(ACCESS_ROLE_ENV)).orElse(DEFAULT_ACCESS_ROLE);

    final String gatekeepRoleName =
        Optional.ofNullable(System.getenv(GATEKEEP_ROLE_ENV)).orElse(DEFAULT_GATEKEEP_ROLE);

    DiscordApi api =
        new DiscordApiBuilder()
            .setToken(token)
            .setAllNonPrivilegedIntentsAnd(Intent.GUILD_MEMBERS, Intent.GUILDS)
            .login()
            .join();

    List<Role> allTargetRoles =
        api.getRolesByNameIgnoreCase(targetRoleName).stream().collect(Collectors.toList());

    if (allTargetRoles.isEmpty()) {
      throw new RuntimeException("Unable to find target role name");
    }

    List<Role> allAccessRoles =
        api.getRolesByNameIgnoreCase(accessRoleName).stream().collect(Collectors.toList());

    if (allAccessRoles.isEmpty()) {
      throw new RuntimeException("Unable to find access role name");
    }

    List<Role> allGatekeepRoles =
        api.getRolesByNameIgnoreCase(gatekeepRoleName).stream().collect(Collectors.toList());

    if (allGatekeepRoles.isEmpty()) {
      throw new RuntimeException("Unable to find gatekeep role name");
    }

    // Handle add Role Events
    api.addUserRoleAddListener(
        event -> {
          Role role = event.getRole();
          User user = event.getUser();
          Server eventServer = event.getServer();

          // If we're adding the target role.
          if (StringUtils.equalsIgnoreCase(targetRoleName, role.getName())) {
            List<Role> otherRoles = user.getRoles(eventServer);
            if (!otherRoles.stream()
                .anyMatch(r -> StringUtils.equalsIgnoreCase(gatekeepRoleName, r.getName()))) {
              logger.warn("Unable to give access... Does not have gatekeep role");
              return;
            }

            addRoleToUser(allAccessRoles, event, "target role added");
            return;
          }

          // If we've added the gatekeep role.
          if (StringUtils.equalsIgnoreCase(gatekeepRoleName, role.getName())) {
            List<Role> otherRoles = user.getRoles(eventServer);
            if (!otherRoles.stream()
                .anyMatch(r -> StringUtils.equalsIgnoreCase(targetRoleName, r.getName()))) {
              logger.warn("Unable to give access... Does not have target role");
              return;
            }

            addRoleToUser(allAccessRoles, event, "gatekeep role removed");
          }
        });

    // Handle delete Role Events
    api.addUserRoleRemoveListener(
        event -> {
          Role role = event.getRole();
          User user = event.getUser();
          Server eventServer = event.getServer();

          if (StringUtils.equalsIgnoreCase(targetRoleName, role.getName())) {
            Optional<Role> accessRoleForServer =
                allAccessRoles.stream()
                    .filter(fRole -> fRole.getServer().getId() == eventServer.getId())
                    .findFirst();
            if (accessRoleForServer.isEmpty()) {
              logger.warn(
                  "Unable to find matching role for server => {}:{} ",
                  eventServer.getId(),
                  eventServer.getName());
              return;
            }

            Role removeRole = accessRoleForServer.get();
            String message =
                "Removing access role in response to target role removal" + removeRole.toString();
            logger.info(message);
            ServerUpdater serverUpdate = new ServerUpdater(event.getServer());
            serverUpdate.removeRoleFromUser(user, removeRole).update();
            return;
          }

          if (StringUtils.equalsIgnoreCase(gatekeepRoleName, role.getName())) {
            Optional<Role> accessRoleForServer =
                allAccessRoles.stream()
                    .filter(fRole -> fRole.getServer().getId() == eventServer.getId())
                    .findFirst();
            if (accessRoleForServer.isEmpty()) {
              logger.warn(
                  "Unable to find matching role for server => {}:{} ",
                  eventServer.getId(),
                  eventServer.getName());
              return;
            }

            Role removeRole = accessRoleForServer.get();
            String message =
                "Removing access role in response to gatekeep role removal" + removeRole.toString();
            logger.info(message);
            ServerUpdater serverUpdate = new ServerUpdater(event.getServer());
            serverUpdate.removeRoleFromUser(user, removeRole).update();
          }
        });
    logger.info("GatekeepRoleDiscordBot started...");
  }

  private void addRoleToUser(List<Role> subscriberAccessRoles, UserRoleEvent event, String reason) {
    User user = event.getUser();
    Server eventServer = event.getServer();
    Optional<Role> accessRoleForServer =
        subscriberAccessRoles.stream()
            .filter(fRole -> fRole.getServer().getId() == eventServer.getId())
            .findFirst();
    if (accessRoleForServer.isEmpty()) {
      logger.warn(
          "Unable to find matching role for server => {}:{} ",
          eventServer.getId(),
          eventServer.getName());
      return;
    }

    Role addRole = accessRoleForServer.get();
    String message =
        "Adding subscriber access role in response to " + reason + " => " + addRole.toString();

    logger.info(message);
    ServerUpdater serverUpdate = new ServerUpdater(event.getServer());
    serverUpdate.addRoleToUser(user, addRole).update();
  }

  public static void main(String[] args) {
    new Main().mainTask();
  }
}
