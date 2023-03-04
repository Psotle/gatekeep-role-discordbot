package io.github.psotle.gatekeeprolediscordbot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Very simple bot to originally designed to allow use of the Twitch Discord Integration with a
 * layer of security.
 *
 * <p>Using the role integrations assigns to users to hang off visibility and other permission is
 * dangerous.
 *
 * <p>This bot utilises another 'access' role to for those permissions and then adds that role to
 * users when they have the integration role assigned to them, and they have another 'gatekeeping'
 * role assigned to them by being permitted by manual vetting into the server.
 *
 * <p>While the bot is offline, moderators will have to step in to reconcile things, as there's no
 * way to access the current list of users and their roles without using an intent that requires
 * OAuth which is a real pain to set up. Covering small blips in high uptime shouldn't be much of an
 * issue.
 *
 * @author Psotle
 */
@SpringBootApplication
public class Main {

  private static final String ACCESS_ROLE_ENV = "ACCESS_ROLE";

  private static final String BOT_TOKEN_ENV = "BOT_TOKEN";

  private static final String DEFAULT_ACCESS_ROLE = "subscriber access";

  private static final String DEFAULT_GATEKEEP_ROLE = "follower";

  private static final String DEFAULT_INTEGRATION_ROLE = "twitch subscriber";

  private static final String GATEKEEP_ROLE_ENV = "GATEKEEP_ROLE";

  private static final String INTEGRATION_ROLE_ENV = "INTEGRATION_ROLE";

  private List<Role> allIntegrationRoles;
  private List<Role> allAccessRoles;
  private List<Role> allGatekeepRoles;
  private String integrationRoleName;
  private String accessRoleName;
  private String gatekeepRoleName;
  private DiscordApi api;

  public static void main(String[] args) {
    new Main().mainTask();
  }

  private final Logger logger = LogManager.getLogger(getClass());

  private void addRoleToMember(User member, Server server) {
    Optional<Role> accessRoleForServer =
        allAccessRoles.stream()
            .filter(fRole -> fRole.getServer().getId() == server.getId())
            .findFirst();

    if (accessRoleForServer.isEmpty()) {
      logger.warn(
          "Unable to find matching role for server => {}:{} ", server.getId(), server.getName());
      return;
    }
    Role addRole = accessRoleForServer.get();
    String message =
        "Adding subscriber access role in response to %s => [%s][%s] %s "
            .formatted(
                "Missing access role",
                member.getDisplayName(server),
                member.getName(),
                server.getName());

    server.addRoleToUser(member, addRole);
    logger.info(message);
  }

  private void removeRoleFromMember(User member, Server server) {
    Optional<Role> accessRoleForServer =
        allAccessRoles.stream()
            .filter(fRole -> fRole.getServer().getId() == server.getId())
            .findFirst();
    if (accessRoleForServer.isEmpty()) {
      logger.warn(
          "Unable to find matching role for server => {}:{} ", server.getId(), server.getName());
      return;
    }
    Role removeRole = accessRoleForServer.get();
    String message =
        "Remove subscriber access role in response to %s => [%s][%s] %s"
            .formatted(
                "No longer has both gatekeep and integration role",
                member.getDisplayName(server),
                member.getName(),
                server.getName());

    server.removeRoleFromUser(member, removeRole);
    logger.info(message);
  }

  public void mainTask() {
    // Handle ENV vars
    integrationRoleName =
        Optional.ofNullable(System.getenv(INTEGRATION_ROLE_ENV)).orElse(DEFAULT_INTEGRATION_ROLE);

    accessRoleName =
        Optional.ofNullable(System.getenv(ACCESS_ROLE_ENV)).orElse(DEFAULT_ACCESS_ROLE);

    gatekeepRoleName =
        Optional.ofNullable(System.getenv(GATEKEEP_ROLE_ENV)).orElse(DEFAULT_GATEKEEP_ROLE);

    api = createApiClient();

    fetchAllRolesForServers();
    reconcileExistingRoles();
    registerEventListeners();

    logger.info("GatekeepRoleDiscordBot started...");
  }

  private DiscordApi createApiClient() {
    String token =
        Optional.ofNullable(System.getenv(BOT_TOKEN_ENV))
            .orElseThrow(() -> new RuntimeException("No BOT_TOKEN set"));
    return new DiscordApiBuilder()
        .setToken(token)
        .setAllNonPrivilegedIntentsAnd(Intent.GUILD_MEMBERS, Intent.GUILDS)
        .setWaitForServersOnStartup(true)
        .setShutdownHookRegistrationEnabled(true)
        .login()
        .join();
  }

  private void fetchAllRolesForServers() {
    allIntegrationRoles = api.getRolesByNameIgnoreCase(integrationRoleName).stream().toList();

    if (allIntegrationRoles.isEmpty()) {
      throw new RuntimeException("Unable to find integration role name");
    }

    allAccessRoles = new ArrayList<>(api.getRolesByNameIgnoreCase(accessRoleName));

    if (allAccessRoles.isEmpty()) {
      throw new RuntimeException("Unable to find access role name");
    }

    allGatekeepRoles = api.getRolesByNameIgnoreCase(gatekeepRoleName).stream().toList();
    if (allGatekeepRoles.isEmpty()) {
      throw new RuntimeException("Unable to find gatekeep role name");
    }
  }

  private void registerEventListeners() {
    logger.info("Listening to events...");
    // Handle add Role Events
    api.addUserRoleAddListener(
        event -> {
          Role addedRole = event.getRole();
          User user = event.getUser();
          Server eventServer = event.getServer();

          if (!StringUtils.equalsIgnoreCase(integrationRoleName, addedRole.getName())
              && !StringUtils.equalsIgnoreCase(gatekeepRoleName, addedRole.getName())) {
            logger.debug("Add event for role we are not interested in..");
            return;
          }

          // If we're adding the integration role.
          Optional<Role> integrationRoleOpt =
              allIntegrationRoles.stream().filter(r -> r.getServer() == eventServer).findFirst();
          Optional<Role> gatekeepRoleOpt =
              allGatekeepRoles.stream().filter(r -> r.getServer() == eventServer).findFirst();
          Optional<Role> accessRoleOpt =
              allAccessRoles.stream()
                  .filter(fRole -> fRole.getServer().getId() == eventServer.getId())
                  .findFirst();

          if (integrationRoleOpt.isEmpty()
              || gatekeepRoleOpt.isEmpty()
              || accessRoleOpt.isEmpty()) {
            logger.info(
                "Not currently configured to handle server {}, please restart bot to obtain fresh role information",
                eventServer);
            return;
          }

          Role integrationRole = integrationRoleOpt.get();
          Role gatekeepRole = gatekeepRoleOpt.get();
          Role accessRole = accessRoleOpt.get();

          List<Role> memberRoles = user.getRoles(eventServer);
          if (memberRoles.contains(accessRole)) {
            logger.debug("already has access role");
            return;
          }
          if (addedRole.equals(integrationRole)) {
            if (memberRoles.stream()
                .noneMatch(r -> StringUtils.equalsIgnoreCase(gatekeepRoleName, r.getName()))) {
              logger.warn("Unable to give access... Does not have gatekeep role");
              return;
            }
            addRoleToMember(user, eventServer);
          } else if (addedRole.equals(gatekeepRole)) {
            if (memberRoles.stream()
                .noneMatch(r -> StringUtils.equalsIgnoreCase(integrationRoleName, r.getName()))) {
              logger.warn("Unable to give access... Does not have integration role");
              return;
            }
            addRoleToMember(user, eventServer);
          } else {
            logger.warn(
                "UNKNOWN ROLE ADDITION DETECTED, NOT EXPECTED ROLE ID. SERVER MAY HAVE CONFIGURATION ISSUES THAT CONFLICT WITH THIS BOT");
          }
        });

    // Handle delete Role Events
    api.addUserRoleRemoveListener(
        event -> {
          Role removedRole = event.getRole();
          User user = event.getUser();
          Server eventServer = event.getServer();

          if (!StringUtils.equalsIgnoreCase(integrationRoleName, removedRole.getName())
              && !StringUtils.equalsIgnoreCase(gatekeepRoleName, removedRole.getName())) {
            logger.debug("Removal event for Role we are not interested in..");
            return;
          }

          // If we're adding the integration role.
          Optional<Role> integrationRoleOpt =
              allIntegrationRoles.stream().filter(r -> r.getServer() == eventServer).findFirst();
          Optional<Role> gatekeepRoleOpt =
              allGatekeepRoles.stream().filter(r -> r.getServer() == eventServer).findFirst();
          Optional<Role> accessRoleOpt =
              allAccessRoles.stream()
                  .filter(fRole -> fRole.getServer().getId() == eventServer.getId())
                  .findFirst();

          if (integrationRoleOpt.isEmpty()
              || gatekeepRoleOpt.isEmpty()
              || accessRoleOpt.isEmpty()) {
            logger.info(
                "Not currently configured to handle server {}, please restart bot to obtain fresh role information",
                eventServer);
            return;
          }

          Role integrationRole = integrationRoleOpt.get();
          Role gatekeepRole = gatekeepRoleOpt.get();
          Role accessRole = accessRoleOpt.get();

          if (removedRole.equals(integrationRole)) {
            String message =
                "Removing access role in response to integration role removal %s"
                    .formatted(removedRole.toString());
            eventServer.removeRoleFromUser(user, accessRole, message).join();
            logger.info(message);
          } else if (removedRole.equals(gatekeepRole)) {
            String message =
                "Removing access role in response to gatekeep role removal %s"
                    .formatted(removedRole.toString());
            eventServer.removeRoleFromUser(user, accessRole, message).join();
            logger.info(message);
          } else {
            logger.warn(
                "UNKNOWN ROLE REMOVED DETECTED, NOT EXPECTED ROLE ID. SERVER MAY HAVE CONFIGURATION ISSUES THAT CONFLICT WITH THIS BOT");
          }
        });
  }

  private void reconcileExistingRoles() {
    logger.info("Reconciling Roles...");

    Set<Server> servers = api.getServers();
    for (Server server : servers) {
      Set<User> members = server.getMembers();

      Optional<Role> integrationRoleOpt =
          allIntegrationRoles.stream().filter(r -> r.getServer() == server).findFirst();
      Optional<Role> gatekeepRoleOpt =
          allGatekeepRoles.stream().filter(r -> r.getServer() == server).findFirst();
      Optional<Role> accessRoleOpt =
          allAccessRoles.stream()
              .filter(fRole -> fRole.getServer().getId() == server.getId())
              .findFirst();

      if (integrationRoleOpt.isEmpty() || gatekeepRoleOpt.isEmpty() || accessRoleOpt.isEmpty()) {
        logger.info(
            "Not currently configured to handle server {}, please restart bot to obtain fresh role information",
            server);
        return;
      }

      for (User member : members) {

        List<Role> memberRoles = member.getRoles(server);

        boolean hasIntegration = memberRoles.contains(integrationRoleOpt.get());
        boolean hasGatekeep = memberRoles.contains(gatekeepRoleOpt.get());
        boolean hasAccess = memberRoles.contains(accessRoleOpt.get());

        logger.info(
            "Reconciling user [{}][{}] for server {} ",
            member.getDisplayName(server),
            member.getName(),
            server.getName());

        // if we have all three roles
        if (hasIntegration && hasGatekeep && hasAccess) {
          logger.info(
              "Member [{}][{}] of Server {} already has all three roles",
              member.getDisplayName(server),
              member.getName(),
              server);
          continue;
        }
        // If we need to add the access role
        if (hasGatekeep && hasIntegration) {
          addRoleToMember(member, server);
        }

        // else check for removal conditions
        if (!hasIntegration && !hasGatekeep && hasAccess) {
          removeRoleFromMember(member, server);
        } else if (hasIntegration && !hasGatekeep && hasAccess) {
          removeRoleFromMember(member, server);
        } else if (!hasIntegration && hasGatekeep && hasAccess) {
          removeRoleFromMember(member, server);
        }
      }
    }
  }
}
