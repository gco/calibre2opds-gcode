package com.gmail.dpierron.calibre.configuration;

/**
 * Specify for each of the modes any properties that
 * need special treatment in that mode.
 *
 * Allows the following to be specified:
 * - The default value for the property for this mode
 * - Whether the item is locked against allowing to change the value
 *
 * Note that any fields that are defined as being used here must be
 * define in the ReadOnlySyanzaConfigurationInterface and have a
 * 'isReadOnly type method defined in ConfigurationHolder.
 */

import com.gmail.dpierron.tools.Helper;


public enum DeviceMode {
  Default("CatalogFolderName", "_catalog", false,
          "TargetFolder", "", true,
          "CopyToDatabaseFolder", true, true,
          "OnlyCatalogAtTarget", false, true,
          "ZipTrookCatalog", false, true),

  Nas("CatalogFolderName", "_catalog", false,
      "TargetFolder", ".", false,
      "CopyToDatabaseFolder", false, false,
      "OnlyCatalogAtTarget", false, false,
      "ZipTrookCatalog", false, true),

  Nook( "CatalogFolderName", "Calibre", false,
        "IncludedFormatsList", "EPUB,PDB,PDF", false,
        "SaveBandwith", true, false,
        "ThumbnailHeight", 144, true,
        "GenerateHtml", false, false,
        "GenerateHtmlDownloads", false, false,
        "GenerateDownloads", true, true,
        "IncludeBooksWithNoFile", false, false,
        "CryptFilenames", false, true,
        "TargetFolder", ".", false,
        "CopyToDatabaseFolder", false, false,
        "OnlyCatalogAtTarget", false, true,
        "IncludeAboutLink", false, false,
        "ZipTrookCatalog", false, false
  );

  Object[] options;

  private DeviceMode(Object... options) {
    this.options = options;
  }

  public Object[] getOptions() {
    return options;
  }

  public static DeviceMode fromName(String name) {
    if (Helper.isNotNullOrEmpty(name)) {
      for (DeviceMode mode : values()) {
        if (mode.name().equalsIgnoreCase(name))
          return mode;
      }
    }
    return Default;
  }

  /**
   * This is used to set options that need to be forced for mode specific settings
   * @param configuration
   */
  public void setModeSpecificOptions(PropertiesBasedConfiguration configuration) {
    for (int i = 0; i < options.length; i += 3) {
      String optionName = (String) options[i];
      Object optionValue = options[i + 1];
      Boolean readOnly = (Boolean) options[i + 2];
      // Decide if we need to force a value
      if ((readOnly == true)
      || (readOnly == false && configuration.isPropertyReadOnly(optionName) == true) ){
        configuration.setProperty(optionName, optionValue);
      }
      configuration.setPropertyReadOnly(optionName, readOnly);
    }
  }
}
