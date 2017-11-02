package one.lindegaard.BagOfGold.update;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.MalformedInputException;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import one.lindegaard.BagOfGold.BagOfGold;
import one.lindegaard.BagOfGold.HttpTools;
import one.lindegaard.BagOfGold.Messages;

public class Updater {

	// ***************************************************************************
	// UPDATECHECK - Check if there is a new version available at
	// https://api.curseforge.com/servermods/files?projectIds=63718
	// ***************************************************************************

	// Update object
	private static BukkitUpdate bukkitUpdate = null;
	private static UpdateStatus updateAvailable = UpdateStatus.UNKNOWN;
	private static String currentJarFile = "";

	public static BukkitUpdate getBukkitUpdate() {
		return bukkitUpdate;
	}

	public static UpdateStatus getUpdateAvailable() {
		return updateAvailable;
	}

	public static void setUpdateAvailable(UpdateStatus b) {
		updateAvailable = b;
	}

	public static String getCurrentJarFile() {
		return currentJarFile;
	}

	public static void setCurrentJarFile(String name) {
		currentJarFile = name;
	}

	public static void hourlyUpdateCheck(final CommandSender sender, boolean updateCheck, final boolean silent) {
		long seconds = BagOfGold.getConfigManager().checkEvery;
		if (seconds < 900) {
			Bukkit.getConsoleSender().sendMessage(ChatColor.RED
					+ "[BagOfGold][Warning] check_every in your config.yml is too low. A low number can cause server crashes. The number is raised to 900 seconds = 15 minutes.");
			seconds = 900;
		}
		if (updateCheck) {
			new BukkitRunnable() {
				@Override
				public void run() {
					pluginUpdateCheck(sender, true, false);
				}
			}.runTaskTimer(BagOfGold.getInstance(), 0L, seconds * 20L);
		}
	}

	public static void pluginUpdateCheck(final CommandSender sender, boolean updateCheck, final boolean silent) {
		if (updateCheck) {
			if (!silent) {
				BagOfGold.getInstance().getServer().getConsoleSender().sendMessage(
						ChatColor.GOLD + "[BagOfGold] " + Messages.getString("bagofgold.commands.update.check"));
			}
			if (updateAvailable != UpdateStatus.RESTART_NEEDED) {
				// Check for updates asynchronously in background
				BagOfGold.getInstance().getServer().getScheduler().runTaskAsynchronously(BagOfGold.getInstance(),
						new Runnable() {
							@Override
							public void run() {
								URL url;
								try {
									url = new URL("https://api.curseforge.com");
									if (HttpTools.isHomePageReachable(url)) {
										bukkitUpdate = new BukkitUpdate(281033); // BagOFGold
										if (!bukkitUpdate.isSuccess()) {
											bukkitUpdate = null;
										}
									} else {
										Messages.debug("Homepage %s seems to be down", url.toString());
									}
								} catch (MalformedURLException e) {
									e.printStackTrace();
								}
							}
						});
				// Check if bukkitUpdate is found in background
				new BukkitRunnable() {
					int count = 0;

					@Override
					public void run() {
						if (count++ > 10) {
							if (!silent)
								sender.sendMessage(ChatColor.RED
										+ "[BagOfGold] No updates found. (No response from server after 10s)");
							this.cancel();
						} else {
							// Wait for the response
							if (bukkitUpdate != null) {
								if (bukkitUpdate.isSuccess()) {
									updateAvailable = isUpdateNewerVersion();
									if (updateAvailable == UpdateStatus.AVAILABLE) {
										sender.sendMessage(ChatColor.GREEN + "[BagOfGold] "
												+ Messages.getString("bagofgold.commands.update.version-found"));
										if (BagOfGold.getConfigManager().autoupdate) {
											downloadAndUpdateJar();
											sender.sendMessage(ChatColor.GREEN + "[BagOfGold] "
													+ Messages.getString("bagofgold.commands.update.complete"));
										} else {
											sender.sendMessage(ChatColor.GREEN + "[BagOfGold] "
													+ Messages.getString("bagofgold.commands.update.help"));
										}

									} else {
										if (!silent) {
											sender.sendMessage(ChatColor.GOLD + "[BagOfGold] "
													+ Messages.getString("bagofgold.commands.update.no-update"));
										}
									}
								}
								this.cancel();
							}

						}
					}
				}.runTaskTimer(BagOfGold.getInstance(), 20L, 20L); // Check
																	// status
				// every second
			} else {
				sender.sendMessage(
						ChatColor.GREEN + "[BagOfGold] " + Messages.getString("bagofgold.commands.update.complete"));
			}
		}
	}

	public static boolean downloadAndUpdateJar() {
		boolean succes = false;
		final String OS = System.getProperty("os.name");
		if (OS.indexOf("Win") >= 0) {
			try {
				succes = downloadFile(getBukkitUpdate().getVersionLink(), "plugins/update/");
				if (succes) {
					File downloadedJar = new File("plugins/update/" + Updater.getBukkitUpdate().getVersionFileName());
					File newJar = new File("plugins/update/BagOfGold.jar");
					if (newJar.exists())
						newJar.delete();
					downloadedJar.renameTo(newJar);
					return true;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			try {
				if (updateAvailable != UpdateStatus.RESTART_NEEDED)
					succes = downloadFile(getBukkitUpdate().getVersionLink(), "plugins/BagOfGold/update/");
				if (succes) {
					File currentJar = new File("plugins/" + getCurrentJarFile());
					File disabledJar = new File("plugins/" + getCurrentJarFile() + ".old");
					int count = 0;
					while (disabledJar.exists() && count++ < 100) {
						disabledJar = new File("plugins/" + getCurrentJarFile() + ".old" + count);
					}
					if (!disabledJar.exists()) {
						currentJar.renameTo(disabledJar);

						File downloadedJar = new File(
								"plugins/BagOfGold/update/" + Updater.getBukkitUpdate().getVersionFileName());
						File newJar = new File("plugins/" + Updater.getBukkitUpdate().getVersionFileName());
						downloadedJar.renameTo(newJar);
						updateAvailable = UpdateStatus.RESTART_NEEDED;
						return true;
					}
				}
			} catch (MalformedInputException malformedInputException) {
				malformedInputException.printStackTrace();
			} catch (IOException ioException) {
				ioException.printStackTrace();
			}
		}
		return false;
	}

	public static UpdateStatus isUpdateNewerVersion() {
		// Version format on Bukkit.org: "BagOfGold Vn.n.n"
		// Version format in jar file: "n.n.n" | "n.n.n-SNAPSHOT-Bn"

		int updateCheck = 0, pluginCheck = 0;
		boolean snapshot = false;
		// Check to see if the latest file is newer that this one
		String[] split = Updater.getBukkitUpdate().getVersionName().split(" V");
		// Only do this if the format is what we expect
		if (split.length == 2) {
			// Need to escape the period in the regex expression
			String[] updateVer = split[1].split("\\.");
			// Check the version #'s
			String[] pluginVerSNAPSHOT = BagOfGold.getInstance().getDescription().getVersion().split("\\-");
			if (pluginVerSNAPSHOT.length > 1)
				snapshot = pluginVerSNAPSHOT[1].equals("SNAPSHOT");
			if (snapshot)
				Messages.debug("You are using a development version (%s)",
						BagOfGold.getInstance().getDescription().getVersion());
			String[] pluginVer = pluginVerSNAPSHOT[0].split("\\.");
			// Run through major, minor, sub
			for (int i = 0; i < Math.max(updateVer.length, pluginVer.length); i++) {
				try {
					updateCheck = 0;
					if (i < updateVer.length) {
						updateCheck = Integer.valueOf(updateVer[i]);
					}
					pluginCheck = 0;
					if (i < pluginVer.length) {
						pluginCheck = Integer.valueOf(pluginVer[i]);
					}
					if (updateCheck > pluginCheck) {
						return UpdateStatus.AVAILABLE;
					} else if (updateCheck < pluginCheck)
						return UpdateStatus.NOT_AVAILABLE;
				} catch (Exception e) {
					BagOfGold.getInstance().getLogger().warning("Could not determine update's version # ");
					BagOfGold.getInstance().getLogger().warning(
							"Installed plugin version: " + BagOfGold.getInstance().getDescription().getVersion());
					BagOfGold.getInstance().getLogger()
							.warning("Newest version on Bukkit.org: " + Updater.getBukkitUpdate().getVersionName());
					return UpdateStatus.UNKNOWN;
				}
			}
		} else {
			BagOfGold.getInstance().getLogger().warning("Could not determine update's version # ");
			BagOfGold.getInstance().getLogger()
					.warning("Installed plugin version: " + BagOfGold.getInstance().getDescription().getVersion());
			BagOfGold.getInstance().getLogger()
					.warning("Newest version on Bukkit.org: " + Updater.getBukkitUpdate().getVersionName());
			return UpdateStatus.UNKNOWN;
		}
		if ((updateCheck == pluginCheck && snapshot))
			return UpdateStatus.AVAILABLE;
		else
			return UpdateStatus.NOT_AVAILABLE;
	}

	private static final int BUFFER_SIZE = 4096;

	/**
	 * Downloads a file from a URL
	 * 
	 * @param fileURL
	 *            HTTP URL of the file to be downloaded
	 * @param saveDir
	 *            path of the directory to save the file
	 * @throws IOException
	 */
	private static boolean downloadFile(String fileURL, String saveDir) throws IOException {
		boolean succes = false;
		URL url = new URL(fileURL);
		HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
		httpConn.setReadTimeout(5000);
		httpConn.setRequestProperty("Accept-Language", "en-US,en);q=0.8");
		httpConn.setRequestProperty("User-Agent", "Mozilla");
		httpConn.setRequestProperty("Referer", "google.com");
		int responseCode = httpConn.getResponseCode();

		System.out.println("[BagOfGold] File to be downloaded:" + fileURL);

		// Create savedir if needed
		if (!new File(saveDir).exists())
			new File(saveDir).mkdirs();

		// if (redirected) then get new URL
		if (responseCode != HttpURLConnection.HTTP_OK) {
			if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM
					|| responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
				String newUrl = httpConn.getHeaderField("Location");
				String cookies = httpConn.getHeaderField("Set-Cookie");
				httpConn = (HttpURLConnection) new URL(newUrl).openConnection();
				httpConn.setRequestProperty("Cookie", cookies);
				httpConn.setRequestProperty("Accept-Language", "en-US,en);q=0.8");
				httpConn.setRequestProperty("User-Agent", "Mozilla");
				httpConn.setRequestProperty("Referer", "google.com");
				System.out.println("[BagOfGold] Redirected file to be downloaded:" + newUrl);
				responseCode = httpConn.getResponseCode();
			}
		}

		// always check HTTP response code first
		if (responseCode == HttpURLConnection.HTTP_OK) {
			String fileName = "";
			String disposition = httpConn.getHeaderField("Content-Disposition");
			String contentType = httpConn.getContentType();
			int contentLength = httpConn.getContentLength();

			if (disposition != null) {
				// extracts file name from header field
				int index = disposition.indexOf("filename=");
				if (index > 0) {
					fileName = disposition.substring(index + 10, disposition.length() - 1);
				}
			} else {
				// extracts file name from URL
				fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1, fileURL.length());
			}

			System.out.println("[BagOfGold] Content-Type = " + contentType);
			System.out.println("[BagOfGold] Content-Disposition = " + disposition);
			System.out.println("[BagOfGold] Content-Length = " + contentLength);
			System.out.println("[BagOfGold] fileName = " + fileName);

			// opens input stream from the HTTP connection
			InputStream inputStream = httpConn.getInputStream();

			String saveFilePath = saveDir + File.separator + fileName;

			// opens an output stream to save into file
			FileOutputStream outputStream = new FileOutputStream(saveFilePath);

			int bytesRead = -1;
			byte[] buffer = new byte[BUFFER_SIZE];
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, bytesRead);
			}

			outputStream.close();
			inputStream.close();

			succes = true;
		} else {
			System.out.println("[BagOfGold] No file to download. Server replied HTTP code: " + responseCode);
		}
		httpConn.disconnect();
		return succes;
	}

}
