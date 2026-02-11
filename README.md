# ext-iv-image-crop
An extension for ImageViewer to allow custom cropping of selected image

## What is this?

This is an extension for the [ImageViewer](https://github.com/scorbo2/imageviewer) application that allows you to apply a custom crop to the selected image.

## How do I get it?

### Option 1: automatic download and install

**New!** Starting with the 2.3 release of ImageViewer, you no longer have to manually build and install application extensions!
Now, you can visit the "Available" tab in the new and improved extension manager dialog:

![Extension manager](extension_manager.jpg "Extension manager")

Select "Crop image" from the list on the left and then hit the "Install" button in the top right.
If you decide later to remove the extension, come back to the extension manager dialog, select "Crop image"
from the list on the left, and hit the "Uninstall" button in the top right. The application will prompt to restart.
It's just that easy!

### Option 2: manual download and install

You can manually download the extension jar:
[ext-iv-image-crop-3.0.0.jar](https://www.corbett.ca/apps/ImageViewer/extensions/3.0/ext-iv-image-crop-3.0.0.jar)

Save it to your ~/.ImageViewer/extensions directory and restart the application.

### Option 3: build from source

You can clone this repo and build the extension jar with Maven (Java 17 or higher required):

```shell
git clone https://github.com/scorbo2/ext-iv-image-crop.git
cd ext-iv-image-crop
mvn package

# Copy the result to extensions dir:
cp target/ext-iv-image-crop-3.0.0.jar ~/.ImageViewer/extensions/
```

## Okay, it's installed, now how do I use it?

Once ImageViewer has restarted, you can select "Crop image" from the "Edit" menu.

TODO screenshots and usage tips

## Requirements

Compatible with any ImageViewer 3.x release.

## License

ImageViewer and this extension are made available under the MIT license: https://opensource.org/license/mit
