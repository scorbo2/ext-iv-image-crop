package ca.corbett.imageviewer.extensions.imagecrop;

import ca.corbett.extras.MessageUtil;
import ca.corbett.extras.gradient.ColorSelectionType;
import ca.corbett.extras.image.ImagePanel;
import ca.corbett.extras.image.ImagePanelConfig;
import ca.corbett.extras.image.ImageUtil;
import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.fields.ColorField;
import ca.corbett.forms.fields.ComboField;
import ca.corbett.forms.fields.LabelField;
import ca.corbett.forms.fields.NumberField;
import ca.corbett.forms.fields.PanelField;
import ca.corbett.imageviewer.extensions.ImageViewerExtensionManager;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides options for cropping a given image, by moving its outer edges inward.
 * Keyboard shortcuts are provided to make interaction with this dialog easier.
 *  TODO mouse wheel support like SquareCropDialog would be even easier
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since ImageViewer 1.2
 */
public class ImageCropDialog extends JDialog {
    private MessageUtil messageUtil;
    private final KeyStrokeManager keyStrokeManager;
    private final File srcFile;
    private BufferedImage originalImage;
    private BufferedImage dBuffer;
    private int imgWidth;
    private int imgHeight;
    private final ImagePanel imagePanel;

    // Original comment: "should be adjustable but UTIL-141 is in the way"
    //    wtf, UTIL-141 was fixed years ago... TODO let's fix this already, sheesh
    private final int CROP_INCREMENT = 10;

    private ColorField cropColorField;
    private ComboField<String> cropLineWidthField;
    private NumberField cropLeftField;
    private NumberField cropTopField;
    private NumberField cropRightField;
    private NumberField cropBottomField;
    private LabelField origAspectRatioLabel;
    private LabelField newAspectRatioLabel;

    /**
     * Creates a new CropDialog based on the image represented by the given file.
     *
     * @param file The File containing the image to be cropped.
     */
    public ImageCropDialog(File file) {
        super(MainWindow.getInstance(), "Crop image", true);
        this.srcFile = file;
        this.keyStrokeManager = new KeyStrokeManager(this);
        addWindowListener(new WindowCleanupListener());
        setMinimumSize(new Dimension(500, 500));
        // Start maximized (or effectively maximized... can't use setExtendedState( MAXIMIZED_BOTH ) on a dialog)
        setSize(new Dimension(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().width,
                              GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height));
        setResizable(true);
        setLocationRelativeTo(MainWindow.getInstance());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        imagePanel = new ImagePanel(ImagePanelConfig.createSimpleReadOnlyProperties());
        add(buildControlPanel(), BorderLayout.WEST);
        add(imagePanel, BorderLayout.CENTER);
        configureKeyboardShortcuts();
        loadImage();
    }

    /**
     * Invoked internally to reset the crop values to 0, which will remove any cropping and show the original image.
     */
    private void resetCrop() {
        cropLeftField.setCurrentValue(0);
        cropTopField.setCurrentValue(0);
        cropRightField.setCurrentValue(0);
        cropBottomField.setCurrentValue(0);
        updateVisibleCrop();
    }

    /**
     * Saves the current crop (if needed) and then closes the dialog.
     * Changes are saved in-place, so as soon as the dialog closes, the main image panel
     * in the MainWindow should already be updated to show the result.
     */
    private void saveCrop() {
        // If there is no crop, just close the dialog without touching the original image:
        if (cropLeftField.getCurrentValue().intValue() == 0
            && cropTopField.getCurrentValue().intValue() == 0
            && cropRightField.getCurrentValue().intValue() == 0
            && cropBottomField.getCurrentValue().intValue() == 0) {
            getMessageUtil().getLogger().info("CropDialog: no changes to save; closing.");
            dispose();
        }

        int newLeft = cropLeftField.getCurrentValue().intValue();
        int newRight = imgWidth - cropRightField.getCurrentValue().intValue();
        int newBottom = imgHeight - cropBottomField.getCurrentValue().intValue();
        int newTop = cropTopField.getCurrentValue().intValue();
        int newWidth = newRight - newLeft;
        int newHeight = newBottom - newTop;

        BufferedImage croppedImage = originalImage.getSubimage(newLeft, newTop, newWidth, newHeight);
        try {
            if (srcFile.getName().toLowerCase().endsWith("png")) {
                // TODO this very obviously should be moved to ImageUtil... entered issue #371 for this
                Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("png");
                ImageWriter imageWriter = null;
                if (iter.hasNext()) {
                    imageWriter = iter.next();
                }
                if (imageWriter == null) {
                    throw new IOException("Unable to find PNG writer on this system.");
                }

                getMessageUtil().getLogger().log(Level.INFO, "CropDialog: saving cropped jpeg image: {0}",
                                                 srcFile.getAbsolutePath());
                ImageUtil.saveImage(croppedImage, srcFile, imageWriter, null);
            }

            else if (srcFile.getName().toLowerCase().endsWith("jpg")
                || srcFile.getName().toLowerCase().endsWith("jpeg")) {
                getMessageUtil().getLogger().log(Level.INFO, "CropDialog: saving cropped png image: {0}",
                                                 srcFile.getAbsolutePath());
                ImageUtil.saveImage(croppedImage, srcFile);
            }

            else {
                throw new IOException("Unsupported image format; must be png or jpeg image.");
            }

            // Force thumbnail regeneration for this image:
            ImageViewerExtensionManager.getInstance().removeThumbnail(srcFile);

            // Force reload of current image in MainWindow:
            MainWindow.getInstance().reloadCurrentImage();

            // We're done here:
            croppedImage.flush();
            dispose();
        }
        catch (IOException ioe) {
            getMessageUtil().error("Problem cropping image: " + ioe.getMessage(), ioe);
        }
    }

    /**
     * Invoked internally to update the visible crop preview in the displayed image.
     */
    private void updateVisibleCrop() {
        // Figure out line thickness based on image size and user selection:
        float denominator = switch (cropLineWidthField.getSelectedIndex()) {
            case 0 -> 250f; // thin
            case 1 -> 125f; // medium
            case 2 -> 75f; // thick
            default -> 250f;
        };
        float lineWidth = imgWidth / denominator;
        lineWidth = Math.max(lineWidth, 1f); // enforce a minimum line width of 1 pixel, for very small images

        // Scale dash pattern relative to line width
        float dashLength = lineWidth * 3f;
        float gapLength = lineWidth * 2.5f;

        Graphics2D graphics = dBuffer.createGraphics();
        graphics.drawImage(originalImage, 0, 0, null);
        graphics.setColor(cropColorField.getColor());
        graphics.setStroke(
            new BasicStroke(lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            1f, new float[]{dashLength, gapLength}, 0f));
        int newLeft = cropLeftField.getCurrentValue().intValue();
        int newRight = imgWidth - cropRightField.getCurrentValue().intValue();
        int newBottom = imgHeight - cropBottomField.getCurrentValue().intValue();
        int newTop = cropTopField.getCurrentValue().intValue();
        graphics.drawLine(newLeft, newTop, newRight, newTop);
        graphics.drawLine(newLeft, newBottom, newRight, newBottom);
        graphics.drawLine(newLeft, newTop, newLeft, newBottom);
        graphics.drawLine(newRight, newTop, newRight, newBottom);
        graphics.dispose();
        imagePanel.setImage(dBuffer);

        // Update aspect ratios:
        float origAspect = imgWidth / (float)imgHeight;
        origAspectRatioLabel.setText(String.format("%1$1.2f", origAspect));
        float newAspect = (newRight - newLeft) / (float)(newBottom - newTop);
        newAspectRatioLabel.setText(String.format("%1$1.2f", newAspect));
    }

    private FormPanel buildControlPanel() {
        final int MAX = Integer.MAX_VALUE; // image dimensions aren't available at form build time, so...
        FormPanel formPanel = new FormPanel();
        formPanel.setBorderMargin(8);
        formPanel.setBorder(BorderFactory.createLoweredBevelBorder());

        cropLeftField = new NumberField("Left crop: ", 0, 0, MAX, CROP_INCREMENT);
        cropLeftField.addValueChangedListener(f -> updateVisibleCrop());
        cropLeftField.getMargins().setLeft(20);
        formPanel.add(cropLeftField);

        LabelField label = new LabelField("(Left/right arrows)");
        label.getMargins().setLeft(15).setBottom(10);
        formPanel.add(label);

        cropTopField = new NumberField("Top crop: ", 0, 0, MAX, CROP_INCREMENT);
        cropTopField.addValueChangedListener(f -> updateVisibleCrop());
        formPanel.add(cropTopField);

        label = new LabelField("(Up/down arrows)");
        label.getMargins().setLeft(15).setBottom(10);
        formPanel.add(label);

        cropRightField = new NumberField("Right crop: ", 0, 0, MAX, CROP_INCREMENT);
        cropRightField.addValueChangedListener(f -> updateVisibleCrop());
        formPanel.add(cropRightField);

        label = new LabelField("(Shift + left/right arrows)");
        label.getMargins().setLeft(15).setBottom(10);
        formPanel.add(label);

        cropBottomField = new NumberField("Bottom crop: ", 0, 0, MAX, CROP_INCREMENT);
        cropBottomField.addValueChangedListener(f -> updateVisibleCrop());
        formPanel.add(cropBottomField);

        label = new LabelField("(Shift + up/down arrows)");
        label.getMargins().setLeft(15).setBottom(30);
        formPanel.add(label);

        label = new LabelField("Hold Ctrl = larger increment");
        label.getMargins().setLeft(15);
        formPanel.add(label);

        label = new LabelField("Hold Alt = smaller increment");
        label.getMargins().setLeft(15);
        formPanel.add(label);

        label = new LabelField("Ctrl+Z = reset crop");
        label.getMargins().setLeft(15);
        formPanel.add(label);

        cropColorField = new ColorField("Crop line color:", ColorSelectionType.SOLID).setColor(Color.RED);
        cropColorField.addValueChangedListener(f -> updateVisibleCrop());
        formPanel.add(cropColorField);

        List<String> options = List.of("Thin", "Medium", "Thick");
        cropLineWidthField = new ComboField<>("Crop line width:", options, 0);
        cropLineWidthField.addValueChangedListener(f -> updateVisibleCrop());
        formPanel.add(cropLineWidthField);

        origAspectRatioLabel = new LabelField("Aspect (orig.):", "N/A");
        label.getMargins().setLeft(15);
        formPanel.add(origAspectRatioLabel);

        newAspectRatioLabel = new LabelField("Aspect (crop):", "N/A");
        label.getMargins().setLeft(15);
        formPanel.add(newAspectRatioLabel);

        PanelField wrapper = new PanelField(new FlowLayout(FlowLayout.CENTER));
        JPanel panel = wrapper.getPanel();
        JButton button = new JButton("Reset");
        button.addActionListener(e -> resetCrop());
        button.setPreferredSize(new Dimension(140, 25));
        panel.add(button);
        wrapper.getMargins().setTop(45);
        formPanel.add(wrapper);

        wrapper = new PanelField();
        panel = wrapper.getPanel();
        panel.setLayout(new FlowLayout(FlowLayout.CENTER));
        button = new JButton("Save and close");
        button.addActionListener(e -> saveCrop());
        button.setPreferredSize(new Dimension(140, 25));
        panel.add(button);
        formPanel.add(wrapper);

        wrapper = new PanelField();
        panel = wrapper.getPanel();
        panel.setLayout(new FlowLayout(FlowLayout.CENTER));
        button = new JButton("Cancel");
        button.addActionListener(e -> dispose());
        button.setPreferredSize(new Dimension(140, 25));
        panel.add(button);
        formPanel.add(wrapper);

        return formPanel;
    }

    private void loadImage() {
        try {
            originalImage = ImageUtil.loadImage(srcFile);
            imgWidth = originalImage.getWidth();
            imgHeight = originalImage.getHeight();
            dBuffer = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
            updateVisibleCrop();
        }
        catch (IOException | ArrayIndexOutOfBoundsException ioe) {
            getMessageUtil().error("Error loading image: " + ioe.getMessage(), ioe);
        }
    }

    /**
     * The keyboard shortcuts on this dialog are complex and currently not configurable,
     * but inline help is shown on the form to hopefully make sense out of them:
     * <ul>
     *     <li>Left/right arrows = move left crop border</li>
     *     <li>Up/down arrows = move top crop border</li>
     *     <li>Shift + left/right arrows = move right crop border</li>
     *     <li>Shift + up/down arrows = move bottom crop border</li>
     *     <li>Hold Ctrl to increase the increment (e.g. for very large images)</li>
     *     <li>Hold Alt to decrease the increment (e.g. for fine tuning)</li>
     *     <li>Ctrl+Z to reset the crop to 0 (i.e. show the original image)</li>
     * </ul>
     * <p>
     *     And, as usual for dialogs, ESC will cancel the dialog, and Enter
     *     will prompt to save the current crop and close the dialog.
     * </p>
     */
    private void configureKeyboardShortcuts() {
        keyStrokeManager.clear();
        keyStrokeManager.registerHandler("esc", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        keyStrokeManager.registerHandler("enter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (getMessageUtil().askYesNo("Confirm",
                                              "Save current crop and close?\nThis will overwrite the original image.")
                    == MessageUtil.YES) {
                    saveCrop();
                    dispose();
                }
            }
        });
        keyStrokeManager.registerHandler("Ctrl+Z", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetCrop();
            }
        });
    }

    /*
    TODO clean up this mess

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        else if (e.getID() == KeyEvent.KEY_PRESSED) {

            int cropLeft = cropLeftField.getCurrentValue().intValue();
            int cropTop = cropTopField.getCurrentValue().intValue();
            int cropRight = imgWidth - cropRightField.getCurrentValue().intValue();
            int cropBottom = imgHeight - cropBottomField.getCurrentValue().intValue();
            int cropIncrement = CROP_INCREMENT; // default value
            if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) > 0) {
                cropIncrement = CROP_INCREMENT * 10; // for very large images
            }
            else if ((e.getModifiersEx() & InputEvent.ALT_DOWN_MASK) > 0) {
                cropIncrement = 2; // for fine tuning
            }

            switch (e.getKeyCode()) {

                // Shift+left = move the right crop border
                // Left = move the left crop border
                case KeyEvent.VK_LEFT:
                    if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) > 0) {
                        adjustCropRightBorder(cropLeft, cropRight, -cropIncrement);
                    }
                    else {
                        adjustCropLeftBorder(cropLeft, cropRight, -cropIncrement);
                    }
                    break;

                // Shift+up = move the bottom crop border
                // Up = move the top crop border
                case KeyEvent.VK_UP:
                    if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) > 0) {
                        adjustCropBottomBorder(cropTop, cropBottom, -cropIncrement);
                    }
                    else {
                        adjustCropTopBorder(cropTop, cropBottom, -cropIncrement);
                    }
                    break;

                // Shift+right = move the right crop border
                // Right = move the left crop border
                case KeyEvent.VK_RIGHT:
                    if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) > 0) {
                        adjustCropRightBorder(cropLeft, cropRight, cropIncrement);
                    }
                    else {
                        adjustCropLeftBorder(cropLeft, cropRight, cropIncrement);
                    }
                    break;

                // Shift + down = move bottom crop border
                // Down = move top crop border
                case KeyEvent.VK_DOWN:
                    if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) > 0) {
                        adjustCropBottomBorder(cropTop, cropBottom, cropIncrement);
                    }
                    else {
                        adjustCropTopBorder(cropTop, cropBottom, cropIncrement);
                    }
                    break;
            }
        }

        // Do NOT allow the event to be redispatched, or our spinners will also respond:
        return true;
    }
    */

    private void adjustCropLeftBorder(int cropLeft, int cropRight, int cropIncrement) {
        getMessageUtil().getLogger().log(Level.FINE, "adjustCropLeftBorder({0},{1},{2})",
                                         new Object[]{cropLeft, cropRight, cropIncrement});
        if (cropIncrement < 0 && cropLeft < Math.abs(cropIncrement)) {
            return;
        }
        if (cropIncrement > 0 && (cropRight - cropLeft) < cropIncrement) {
            return;
        }
        cropLeftField.setCurrentValue(cropLeft + cropIncrement);
    }

    private void adjustCropTopBorder(int cropTop, int cropBottom, int cropIncrement) {
        getMessageUtil().getLogger().log(Level.FINE, "adjustCropTopBorder({0},{1},{2})",
                                         new Object[]{cropTop, cropBottom, cropIncrement});
        if (cropIncrement < 0 && cropTop < Math.abs(cropIncrement)) {
            return;
        }
        if (cropIncrement > 0 && (cropBottom - cropTop) < cropIncrement) {
            return;
        }
        cropTopField.setCurrentValue(cropTop + cropIncrement);
    }

    private void adjustCropRightBorder(int cropLeft, int cropRight, int cropIncrement) {
        getMessageUtil().getLogger().log(Level.FINE, "adjustCropRightBorder({0},{1},{2})",
                                         new Object[]{cropLeft, cropRight, cropIncrement});
        if (cropIncrement < 0 && (cropRight - cropLeft) < Math.abs(cropIncrement)) {
            return;
        }
        if (cropIncrement > 0 && (imgWidth - cropIncrement) < cropRight) {
            return;
        }
        cropRightField.setCurrentValue(imgWidth - cropRight - cropIncrement);
    }

    private void adjustCropBottomBorder(int cropTop, int cropBottom, int cropIncrement) {
        getMessageUtil().getLogger().log(Level.FINE, "adjustBottomTopBorder({0},{1},{2})",
                                         new Object[]{cropTop, cropBottom, cropIncrement});
        if (cropIncrement < 0 && (cropBottom - cropTop) < Math.abs(cropIncrement)) {
            return;
        }
        if (cropIncrement > 0 && (imgHeight - cropIncrement) < cropBottom) {
            return;
        }
        cropBottomField.setCurrentValue(imgHeight - cropBottom - cropIncrement);

    }

    private MessageUtil getMessageUtil() {
        if (messageUtil == null) {
            messageUtil = new MessageUtil(this, Logger.getLogger(ImageCropDialog.class.getName()));
        }
        return messageUtil;
    }

    /**
     * Idempotent cleanup method.
     */
    private void cleanup() {
        if (originalImage != null) {
            imagePanel.dispose();
            keyStrokeManager.dispose();
            originalImage.flush();
            dBuffer.flush();
            originalImage = null;
            dBuffer = null;
        }
    }

    /**
     * Simple cleanup class to ensure that our cleanup is invoked no matter how the window
     * is closed - either by our own close button, or by the user clicking the "X" button on
     * the window frame.
     */
    private class WindowCleanupListener extends WindowAdapter {
        @Override
        public void windowClosing(WindowEvent e) {
            cleanup();
        }

        @Override
        public void windowClosed(WindowEvent e) {
            cleanup();
        }
    }
}
