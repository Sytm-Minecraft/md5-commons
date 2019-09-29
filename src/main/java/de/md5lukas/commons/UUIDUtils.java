package de.md5lukas.commons;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.md5lukas.commons.internal.CommonsMain;
import de.md5lukas.commons.internal.Md5CommonsConfig;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

public class UUIDUtils {

	public static final UUID ZERO_UUID = new UUID(0, 0);
	private static final Pattern UUID_PATTERN = Pattern.compile("\\b[0-9a-f]{8}\\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\\b[0-9a-f]{12}\\b");
	private static final String UUID_URL = "https://api.mojang.com/users/profiles/minecraft/%s";
	private static final String NAME_URL = "https://api.mojang.com/user/profiles/%s/names";
	private static final LoadingCache<String, UUID> uuidCache;
	private static final LoadingCache<UUID, String> nameCache;

	static {
		uuidCache = CacheBuilder.newBuilder().maximumSize(Md5CommonsConfig.getUuidCacheMaxSize())
			.expireAfterWrite(Md5CommonsConfig.getUuidCacheExpireAfter(), Md5CommonsConfig.getUuidCacheExpireAfterTu()).build(new CacheLoader<String, UUID>() {
				@Override
				public UUID load(String name) throws Exception {
					if (name.length() < 3)
						throw new IllegalArgumentException("The name provided for uuid look-up is too short");
					name = name.toLowerCase();
					if (Bukkit.getPlayerExact(name) != null) {
						return Bukkit.getPlayerExact(name).getUniqueId();
					}
					HttpURLConnection connection = (HttpURLConnection) new URL(String.format(UUID_URL, name)).openConnection();
					connection.setReadTimeout(500);
					JsonObject json = new JsonParser()
						.parse(new BufferedReader(new InputStreamReader(connection.getInputStream())).readLine())
						.getAsJsonObject();
					return uuidFromTrimmed(json.get("id").getAsString());
				}
			});
		nameCache = CacheBuilder.newBuilder().maximumSize(Md5CommonsConfig.getUuidCacheMaxSize())
			.expireAfterWrite(Md5CommonsConfig.getUuidCacheExpireAfter(), Md5CommonsConfig.getUuidCacheExpireAfterTu()).build(new CacheLoader<UUID, String>() {
				@Override
				public String load(UUID uuid) throws Exception {
					if (Bukkit.getPlayer(uuid) != null) {
						return Bukkit.getPlayer(uuid).getName();
					}
					HttpURLConnection connection = (HttpURLConnection) new URL(
						String.format(NAME_URL, trimUUID(uuid))).openConnection();
					connection.setReadTimeout(500);

					JsonArray jsonArray = new JsonParser()
						.parse(new BufferedReader(new InputStreamReader(connection.getInputStream())).readLine())
						.getAsJsonArray();
					JsonObject json = jsonArray.get(jsonArray.size() - 1).getAsJsonObject();
					String name = json.get("name").getAsString();
					uuidCache.put(name.toLowerCase(), uuid);
					return name;
				}
			});
	}

	public static UUID getUUID(String name) {
		try {
			return uuidCache.get(checkNotNull(name, "The name can't be null").toLowerCase());
		} catch (ExecutionException ee) {
			if (ee.getCause() instanceof SocketTimeoutException || ee.getCause() instanceof IOException) {
				CommonsMain.logger().log(Level.SEVERE, "An error occurred while trying to retrieve the uuid of the player '" + name + "'", ee);
			}
		}
		return null;
	}

	public static String getName(UUID uuid) {
		try {
			return nameCache.get(checkNotNull(uuid, "The uuid can't be null"));
		} catch (ExecutionException ee) {
			if (ee.getCause() instanceof SocketTimeoutException || ee.getCause() instanceof IOException) {
				CommonsMain.logger().log(Level.SEVERE, "An error occurred while trying to retrieve the name of the player with the uuid of " + uuid, ee);
			}
		}
		return null;
	}

	public static void getUUID(String name, Consumer<UUID> callback) {
		checkNotNull(name, "The name cannot be null!");
		Bukkit.getScheduler().runTaskAsynchronously(CommonsMain.instance(), () -> {
			try {
				UUID uuid = uuidCache.get(name.toLowerCase());
				Bukkit.getScheduler().runTask(CommonsMain.instance(), () -> {
					callback.accept(uuid);
				});
				return;
			} catch (ExecutionException ee) {
				CommonsMain.logger().log(Level.SEVERE, "An error occurred while trying to retrieve the uuid of the player '" + name + "'", ee);
			}
			Bukkit.getScheduler().runTask(CommonsMain.instance(), () -> {
				callback.accept(null);
			});
		});
	}

	public static void getName(UUID uuid, Consumer<String> callback) {
		checkNotNull(uuid, "The uuid cannot be null!");
		Bukkit.getScheduler().runTaskAsynchronously(CommonsMain.instance(), () -> {
			try {
				String name = nameCache.get(uuid);
				Bukkit.getScheduler().runTask(CommonsMain.instance(), () -> {
					callback.accept(name);
				});
				return;
			} catch (ExecutionException ee) {
				CommonsMain.logger().log(Level.SEVERE, "An error occurred while trying to retrieve the name of the player with the uuid of " + uuid, ee);
			}
			Bukkit.getScheduler().runTask(CommonsMain.instance(), () -> {
				callback.accept(null);
			});
		});
	}

	public static boolean isUUID(String input) {
		return UUID_PATTERN.matcher(checkNotNull(input, "The input to check can't be null")).matches();
	}

	private static String trimUUID(UUID uuid) {
		return uuid.toString().replace("-", "");
	}

	private static UUID uuidFromTrimmed(String input) {
		StringBuilder builder = new StringBuilder(input);
		builder.insert(8, '-').insert(13, '-').insert(18, '-').insert(23, '-');
		return UUID.fromString(builder.toString());
	}
}