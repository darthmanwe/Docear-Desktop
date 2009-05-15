package org.freeplane.web;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;

import org.freeplane.core.controller.Controller;
import org.freeplane.core.controller.FreeplaneVersion;
import org.freeplane.core.frame.IMapViewChangeListener;
import org.freeplane.core.resources.FpStringUtils;
import org.freeplane.core.resources.ResourceBundles;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.MenuBuilder;
import org.freeplane.core.util.LogTool;

/**
 * Checks for updates.
 * 
 * @author robert ladstaetter
 */
public class UpdateCheckAction extends AFreeplaneAction {
	private static final int CHECK_TIME = 30 * 1000;
	private static final String LAST_UPDATE_CHECK_TIME = "last_update_check_time";
	private static final String CHECK_UPDATES_AUTOMATICALLY = "check_updates_automatically";
	private static final int TWO_DAYS = 1*24*60*60*1000;
	private static boolean autorunEnabled  = true;
	private static Timer autorunTimer = null;
	/**
	 * the sid.
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * the url where to download the newest version
	 */
	private static final String WEB_DOWNLOAD_LOCATION_KEY = "webDownloadLocation";
	/**
	 * the url to check the local version against
	 */
	private static final String WEB_UPDATE_LOCATION_KEY = "webUpdateLocation";
	private static final String UPDATE_BUTTON_LOCATION = "/main_toolbar/update";
	private static final String UPDATE_BUTTON_PATH = UPDATE_BUTTON_LOCATION + "/checkUpdate";
	private static final String LAST_UPDATE_VERSION = "last_update_verson";
	/**
	 * the client which asks a remote repository for the current version of the program.
	 */
	public UpdateCheckAction(final Controller controller) {
		super("UpdateCheckAction", controller);
		controller.getMapViewManager().addMapViewChangeListener(new IMapViewChangeListener(){

			public void afterViewChange(Component oldView, Component newView) {
				if(newView == null){
					return;
				}
				EventQueue.invokeLater(new Runnable(){

					public void run() {
						removeMe(controller);
                    }});
				setTimer();
            }

			private void removeMe(final Controller controller) {
	            controller.getMapViewManager().removeMapViewChangeListener(this);
            }

			public void afterViewClose(Component oldView) {
            }

			public void afterViewCreated(Component mapView) {
            }

			public void beforeViewChange(Component oldView, Component newView) {
            }});
	}

	private void setTimer() {
	    if(autorunEnabled == false){
			return;
		}
		autorunEnabled = ResourceController.getResourceController().getBooleanProperty(CHECK_UPDATES_AUTOMATICALLY);
		if(autorunEnabled == false){
			return;
		}
		autorunEnabled = false;
		final Date now = new Date();
		final long nextCheckMillis = ResourceController.getResourceController().getLongProperty(LAST_UPDATE_CHECK_TIME, 0) + TWO_DAYS;
		final Date nextCheckDate = new Date(nextCheckMillis);
		if(now.before(nextCheckDate)){
			final FreeplaneVersion knownNewVersion = getKnownNewVersion();
			addUpdateButton(knownNewVersion);
			return;
		}
		autorunTimer = new Timer(CHECK_TIME, this);
		autorunTimer.setRepeats(false);
		autorunTimer.start();
    }

	public void actionPerformed(final ActionEvent e) {
		final boolean autoRun = e.getSource().equals(autorunTimer);
		if(autorunTimer != null){
			autorunTimer.stop();
			autorunTimer = null;
		}
		new Thread(new Runnable(){
			public void run() {
				checkForUpdates(autoRun);
	            
            }}, "checkForUpdates").start();
	}

	private void checkForUpdates(final boolean autoRun) {
		final Date now = new Date();
		ResourceController.getResourceController().setProperty(LAST_UPDATE_CHECK_TIME, Long.toString(now.getTime()));
	    final Locale defaultLocale = Locale.getDefault();
		final String language = defaultLocale.getLanguage();
		final String DEFAULT_LANGUAGE="en";
		final String webUpdateUrl = ResourceController.getResourceController().getProperty(
			WEB_UPDATE_LOCATION_KEY);
		final String translatedWebUpdate = webUpdateUrl + "history_" + language +".txt";
		final FreeplaneVersion localVersion = FreeplaneVersion.getVersion();
		final HttpVersionClient translatedVersionClient = new HttpVersionClient(translatedWebUpdate, localVersion);
		FreeplaneVersion lastTranslatedVersion = translatedVersionClient.getRemoteVersion();
		if(lastTranslatedVersion == null){
			lastTranslatedVersion = localVersion;
		}
		
		final String history;
		final FreeplaneVersion lastVersion;
		final boolean connectSuccesfull;
		if(! language.equals(DEFAULT_LANGUAGE)){
			final String defaultWebUpdate = webUpdateUrl + "history_" + DEFAULT_LANGUAGE +".txt";
			final HttpVersionClient defaultVersionClient = new HttpVersionClient(defaultWebUpdate, lastTranslatedVersion);
			lastVersion= defaultVersionClient.getRemoteVersion();
			history = defaultVersionClient.getHistory() + translatedVersionClient.getHistory();
			connectSuccesfull = defaultVersionClient.isSuccessful();
		}
		else{
			lastVersion = lastTranslatedVersion;
			history = translatedVersionClient.getHistory();
			connectSuccesfull = translatedVersionClient.isSuccessful();
		}
		EventQueue.invokeLater(new Runnable(){

			public void run() {
				addUpdateButton(lastVersion);
				if(autoRun){
					return;
				}
				showUpdateDialog(connectSuccesfull, localVersion, lastVersion, history);
            }});
    }

	private FreeplaneVersion getKnownNewVersion() {
		final FreeplaneVersion localVersion = FreeplaneVersion.getVersion();
		final String property = ResourceController.getResourceController().getProperty(LAST_UPDATE_VERSION);
		if(property.equals("")){
			return null;
		}
		FreeplaneVersion lastVersion = FreeplaneVersion.getVersion(property);
		if(lastVersion.compareTo(localVersion) <= 0){
			lastVersion = null;
		}
	    return lastVersion;
    }

	private void addUpdateButton(FreeplaneVersion lastVersion) {
		final Set<String> modes = getController().getModes();
		for(String mode:modes){
			final MenuBuilder menuBuilder = getController().getModeController(mode).getUserInputListenerFactory().getMenuBuilder();
			if(lastVersion == null || lastVersion.compareTo(FreeplaneVersion.getVersion())<= 0){
				ResourceController.getResourceController().setProperty(LAST_UPDATE_VERSION, "");
				if(menuBuilder.get(UPDATE_BUTTON_PATH) != null){
					menuBuilder.removeElement(UPDATE_BUTTON_PATH);
				}
				continue;
			}
			ResourceController.getResourceController().setProperty(LAST_UPDATE_VERSION, lastVersion.toString());
			final String updateAvailable = FpStringUtils.formatText("new_version_available", lastVersion.toString());
			putValue(SHORT_DESCRIPTION, updateAvailable);
			putValue(LONG_DESCRIPTION, updateAvailable);
			if(menuBuilder.get(UPDATE_BUTTON_PATH) == null){
				menuBuilder.addAction(UPDATE_BUTTON_LOCATION, UPDATE_BUTTON_PATH, UpdateCheckAction.this, MenuBuilder.AS_CHILD);
			}
		}
	}

	private void showUpdateDialog(boolean connectSuccesfull, final FreeplaneVersion localVersion, final FreeplaneVersion newVersion,
                                  String history) {
		final int choice;
	    if (connectSuccesfull == false) {
	    	choice =showUpdateDialog("can_not_connect_to_info_server", "", "");
		}
	    else if (localVersion.compareTo(newVersion) < 0) {
	    	choice = showUpdateDialog("new_version_available", newVersion.toString(), history);
	    }
	    else{
			showUpdateDialog("version_up_to_date", null, null);
	    	choice =-1;
	    }
	    if (0 != choice){
	    	return;
	    }
	    // go to download page
	    try {
	    	getController().getViewController().openDocument(
	    		new URL(ResourceController.getResourceController().getProperty(WEB_DOWNLOAD_LOCATION_KEY)));
	    }
	    catch (final MalformedURLException ex) {
	    	getController().errorMessage(ResourceBundles.getText("url_error") + "\n" + ex);
	    }
	    catch (final Exception ex) {
	    	getController().errorMessage(ex);
	    }
    }

	private int showUpdateDialog(final String info, final String newVersion, String history) {
	    final Box messagePane = Box.createVerticalBox();
	    final JLabel messageLabel;
	    if(newVersion != null){
	    	messageLabel = new JLabel(FpStringUtils.formatText(info, newVersion.toString()));
	    }
	    else{
	    	messageLabel = new JLabel(ResourceBundles.getText(info));
	    }
	    messageLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
	    messagePane.add(messageLabel);
	    final JTextArea historyArea = new JTextArea(history);
	    historyArea.setEditable(false);
	    final JScrollPane historyPane = new JScrollPane(historyArea);
	    historyPane.setPreferredSize(new Dimension(500, 300));
	    historyPane.setAlignmentX(JLabel.LEFT_ALIGNMENT);
	    messagePane.add(historyPane);			
	    final JCheckBox updateAutomatically = new JCheckBox(ResourceBundles.getText("OptionPanel.check_updates_automatically"),
		    ResourceController.getResourceController().getBooleanProperty(CHECK_UPDATES_AUTOMATICALLY));
	    updateAutomatically.setAlignmentX(JLabel.LEFT_ALIGNMENT);
	    messagePane.add(updateAutomatically);
	    final Object[] options;
	    if(newVersion != null){
	    	options = new Object[]{ResourceBundles.getText("download"), 
	    		FpStringUtils.removeMnemonic(ResourceBundles.getText("cancel"))};
	    }
	    else{
	    	options = new Object[]{FpStringUtils.removeMnemonic(ResourceBundles.getText("CloseAction.text"))};
	    }
	    final int choice = JOptionPane.showOptionDialog(getController().getViewController().getFrame(), 
	    	messagePane, ResourceBundles.getText("updatecheckdialog"), JOptionPane.DEFAULT_OPTION, 
	    	JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
	    ResourceController.getResourceController().setProperty(CHECK_UPDATES_AUTOMATICALLY,
		    Boolean.toString(updateAutomatically.isSelected()));
	    return choice;
    }
}
