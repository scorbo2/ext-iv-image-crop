package ca.corbett.imageviewer.extensions.imagecrop;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;

import java.util.List;

public class ImageCropExtension extends ImageViewerExtension {
    private final AppExtensionInfo extInfo;

    public ImageCropExtension() {
        extInfo = AppExtensionInfo.fromExtensionJar(getClass(),"/ca/corbett/imageviewer/extensions/imageresize/extInfo.json");
        if (extInfo == null) {
            throw new RuntimeException("ImageResizeExtension: can't parse extInfo.json!");
        }
    }

    @Override
    public AppExtensionInfo getInfo() {
        return extInfo;
    }

    @Override
    protected List<AbstractProperty> createConfigProperties() {
        return List.of();
    }

    @Override
    protected void loadJarResources() {

    }
}
