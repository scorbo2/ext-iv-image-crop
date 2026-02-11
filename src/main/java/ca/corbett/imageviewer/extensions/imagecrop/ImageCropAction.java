package ca.corbett.imageviewer.extensions.imagecrop;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;

import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Launches the ImageCropDialog based on the currently selected image, if there is one.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class ImageCropAction extends EnhancedAction {

    private static ImageCropAction instance;
    private static final String NAME = "Crop image...";

    private ImageCropAction() {
        super(NAME); // no icon for this action
    }

    public static ImageCropAction getInstance() {
        if (instance == null) {
            instance = new ImageCropAction();
        }
        return instance;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ImageInstance currentImage = MainWindow.getInstance().getSelectedImage();
        if (currentImage.isEmpty()) {
            MainWindow.getInstance().showMessageDialog("Crop image", "Nothing selected.");
            return;
        }

        // Ensure correct file format:
        File file = currentImage.getImageFile();
        String filename = file.getName().toLowerCase();
        if (!filename.endsWith("jpg")
            && !filename.endsWith("jpeg")
            && !filename.endsWith("png")) {
            MainWindow.getInstance().showMessageDialog("Crop image",
                                                       "Image cropping can currently only be performed on jpeg or png images.");
            return;
        }

        new ImageCropDialog(file).setVisible(true);
    }
}
