package dev.sirbepy.mc.safechests.grant;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reads and atomically writes {@code grants.yml}: timed/permanent whitelist grants and pending one-time grants. */
public final class GrantStore {

    private static final String PERMANENT = "permanent";

    private final File file;
    private final Logger logger;

    public GrantStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public List<WhitelistGrant> loadWhitelistGrants() {
        List<WhitelistGrant> grants = new ArrayList<>();
        YamlConfiguration yaml = read();
        ConfigurationSection whitelist = yaml.getConfigurationSection("whitelist");
        if (whitelist == null) {
            return grants;
        }
        for (String ownerKey : whitelist.getKeys(false)) {
            UUID owner = parseUuid(ownerKey);
            ConfigurationSection grantees = whitelist.getConfigurationSection(ownerKey);
            if (owner == null || grantees == null) {
                continue;
            }
            for (String granteeKey : grantees.getKeys(false)) {
                UUID grantee = parseUuid(granteeKey);
                if (grantee == null) {
                    continue;
                }
                String value = grantees.getString(granteeKey);
                Instant expiresAt = PERMANENT.equals(value) ? null : Instant.ofEpochSecond(Long.parseLong(value));
                grants.add(new WhitelistGrant(owner, grantee, expiresAt));
            }
        }
        return grants;
    }

    public void saveWhitelistGrants(List<WhitelistGrant> grants) {
        YamlConfiguration yaml = read();
        yaml.set("whitelist", null);
        for (WhitelistGrant grant : grants) {
            String path = "whitelist." + grant.owner() + "." + grant.grantee();
            yaml.set(path, grant.isPermanent() ? PERMANENT : String.valueOf(grant.expiresAt().getEpochSecond()));
        }
        write(yaml);
    }

    public List<OneTimeGrant> loadOneTimeGrants() {
        List<OneTimeGrant> grants = new ArrayList<>();
        YamlConfiguration yaml = read();
        List<?> rawList = yaml.getList("one-time");
        if (rawList == null) {
            return grants;
        }
        for (Object raw : rawList) {
            if (raw instanceof ConfigurationSection section) {
                grants.add(fromSection(section));
            } else if (raw instanceof java.util.Map<?, ?> map) {
                YamlConfiguration adapter = new YamlConfiguration();
                for (var entry : map.entrySet()) {
                    adapter.set(String.valueOf(entry.getKey()), entry.getValue());
                }
                grants.add(fromSection(adapter));
            }
        }
        return grants;
    }

    public void saveOneTimeGrants(List<OneTimeGrant> grants) {
        YamlConfiguration yaml = read();
        List<java.util.Map<String, Object>> serialized = new ArrayList<>();
        for (OneTimeGrant grant : grants) {
            java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("owner", grant.owner().toString());
            entry.put("grantee", grant.grantee().toString());
            entry.put("world", grant.world());
            entry.put("x", grant.x());
            entry.put("y", grant.y());
            entry.put("z", grant.z());
            serialized.add(entry);
        }
        yaml.set("one-time", serialized);
        write(yaml);
    }

    private OneTimeGrant fromSection(ConfigurationSection section) {
        return new OneTimeGrant(
                parseUuid(section.getString("owner")),
                parseUuid(section.getString("grantee")),
                section.getString("world"),
                section.getInt("x"),
                section.getInt("y"),
                section.getInt("z")
        );
    }

    private UUID parseUuid(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Ignoring malformed UUID in grants.yml: " + raw, e);
            return null;
        }
    }

    private YamlConfiguration read() {
        try {
            return YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to read grants.yml, starting from an empty file", e);
            return new YamlConfiguration();
        }
    }

    private void write(YamlConfiguration yaml) {
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save grants.yml", e);
        }
    }

    // Deduplicates owner keys so repeated saves do not accumulate orphaned entries from removed grantees.
    public static Set<UUID> ownersOf(List<WhitelistGrant> grants) {
        Set<UUID> owners = new HashSet<>();
        for (WhitelistGrant grant : grants) {
            owners.add(grant.owner());
        }
        return owners;
    }
}
