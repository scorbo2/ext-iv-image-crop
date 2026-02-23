package ca.corbett.imageviewer.extensions.imagecrop;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.EnhancedAction;
import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.KeyStrokeProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;
import ca.corbett.imageviewer.ui.MainWindow;

import java.util.ArrayList;
import java.util.List;

/**
 * An ImageViewer extension that provides a dialog for simple square or rectangular cropping
 * of images.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since ImageViewer 3.0 (ported from much older 2.x ImageCropper extension)
 */
public class ImageCropExtension extends ImageViewerExtension {
    private static final String keystrokeProp = AppConfig.KEYSTROKE_MISC_PREFIX + "imageCrop";
    private final AppExtensionInfo extInfo;

    public ImageCropExtension() {
        extInfo = AppExtensionInfo.fromExtensionJar(getClass(),
                                                    "/ca/corbett/imageviewer/extensions/imagecrop/extInfo.json");
        if (extInfo == null) {
            throw new RuntimeException("ImageCropExtension: can't parse extInfo.json!");
        }
    }

    @Override
    public AppExtensionInfo getInfo() {
        return extInfo;
    }

    @Override
    protected List<AbstractProperty> createConfigProperties() {
        List<AbstractProperty> props = new ArrayList<>();

        props.add(new KeyStrokeProperty(keystrokeProp,
                                        "Crop image:",
                                        KeyStrokeManager.parseKeyStroke("Ctrl+Shift+C"),
                                        ImageCropAction.getInstance())
                      .setAllowBlank(true)
                      .setReservedKeyStrokes(AppConfig.RESERVED_KEYSTROKES)
                      .setHelpText("Show the image crop dialog"));

        return props;
    }

    @Override
    public List<EnhancedAction> getMenuActions(String topLevelMenu, MainWindow.BrowseMode browseMode) {
        if ("Edit".equals(topLevelMenu)) {
            return List.of(ImageCropAction.getInstance());
        }
        return null;
    }

    @Override
    public List<EnhancedAction> getPopupMenuActions(MainWindow.BrowseMode browseMode) {
        return List.of(ImageCropAction.getInstance());
    }

    @Override
    protected void loadJarResources() {
        // Nothing to load here for this extension
    }
}
