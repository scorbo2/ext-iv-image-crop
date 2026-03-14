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

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides options for cropping a given image, by moving its outer edges inward.
 * Keyboard shortcuts are provided to make interaction with this dialog easier.
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

    // Cheesy constants for adjusting borders:
    private static final int NEGATIVE = -1; // move left for vertical borders, move up for horizontal borders
    private static final int POSITIVE = 1; // move right for vertical borders, move down for horizontal borders

    // Constants for crop increments:
    private static final int SMALL_INCREMENT = 1;
    private static final int MEDIUM_INCREMENT = 10;
    private static final int LARGE_INCREMENT = 100;
    private int currentIncrement = MEDIUM_INCREMENT;

    private ColorField cropColorField;
    private ComboField<String> cropLineWidthField;
    private NumberField cropLeftField;
    private NumberField cropTopField;
    private NumberField cropRightField;
    private NumberField cropBottomField;
    private JToggleButton btnSmallMove;
    private JToggleButton btnMediumMove;
    private JToggleButton btnLargeMove;
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
     * Prompts the user for confirmation, and if confirmed,
     * saves the current crop (if needed) and then closes the dialog.
     * If the user does not confirm, the dialog does NOT close.
     */
    private void promptToSaveAndExit() {
        if (getMessageUtil().askYesNo("Confirm",
                                      "Save current crop and close?\nThis will overwrite the original image.")
            == MessageUtil.YES) {
            saveCrop();
            dispose();
        }
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
            return; // we're done here.
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
                getMessageUtil().getLogger().log(Level.INFO, "CropDialog: saving cropped png image: {0}",
                                                 srcFile.getAbsolutePath());
                ImageUtil.savePngImage(croppedImage, srcFile);
            }

            else if (srcFile.getName().toLowerCase().endsWith("jpg")
                || srcFile.getName().toLowerCase().endsWith("jpeg")) {
                getMessageUtil().getLogger().log(Level.INFO, "CropDialog: saving cropped jpeg image: {0}",
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
     * Updates our toggle buttons according to the given movement increment,
     * which must be one of our constants SMALL_INCREMENT, MEDIUM_INCREMENT, or LARGE_INCREMENT.
     */
    private void setCropMovement(int movement) {
        btnSmallMove.setSelected(movement == SMALL_INCREMENT);
        btnMediumMove.setSelected(movement == MEDIUM_INCREMENT);
        btnLargeMove.setSelected(movement == LARGE_INCREMENT);
        currentIncrement = movement;
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
        formPanel.setBorderMargin(10);
        formPanel.setBorder(BorderFactory.createLoweredBevelBorder());

        final int headerSize = 16;
        formPanel.add(LabelField.createBoldHeaderLabel("Current crop:", headerSize));

        cropLeftField = new NumberField("Left crop: ", 0, 0, MAX, MEDIUM_INCREMENT);
        cropLeftField.addValueChangedListener(f -> updateVisibleCrop());
        cropLeftField.setEnabled(false); // force keyboard shortcuts and prevent garbage input
        formPanel.add(cropLeftField);

        cropTopField = new NumberField("Top crop: ", 0, 0, MAX, MEDIUM_INCREMENT);
        cropTopField.addValueChangedListener(f -> updateVisibleCrop());
        cropTopField.setEnabled(false); // force keyboard shortcuts and prevent garbage input
        formPanel.add(cropTopField);

        cropRightField = new NumberField("Right crop: ", 0, 0, MAX, MEDIUM_INCREMENT);
        cropRightField.addValueChangedListener(f -> updateVisibleCrop());
        cropRightField.setEnabled(false); // force keyboard shortcuts and prevent garbage input
        formPanel.add(cropRightField);

        cropBottomField = new NumberField("Bottom crop: ", 0, 0, MAX, MEDIUM_INCREMENT);
        cropBottomField.addValueChangedListener(f -> updateVisibleCrop());
        cropBottomField.setEnabled(false); // force keyboard shortcuts and prevent garbage input
        formPanel.add(cropBottomField);

        origAspectRatioLabel = new LabelField("Aspect (orig.):", "N/A");
        formPanel.add(origAspectRatioLabel);

        newAspectRatioLabel = new LabelField("Aspect (crop):", "N/A");
        formPanel.add(newAspectRatioLabel);

        formPanel.add(LabelField.createBoldHeaderLabel("Crop movement:", headerSize));

        PanelField panelField = new PanelField(new FlowLayout(FlowLayout.CENTER));
        JPanel panel = panelField.getPanel();
        btnSmallMove = new JToggleButton("Small");
        btnSmallMove.addActionListener(e -> setCropMovement(SMALL_INCREMENT));
        btnMediumMove = new JToggleButton("Medium");
        btnMediumMove.addActionListener(e -> setCropMovement(MEDIUM_INCREMENT));
        btnLargeMove = new JToggleButton("Large");
        btnLargeMove.addActionListener(e -> setCropMovement(LARGE_INCREMENT));
        btnMediumMove.setSelected(true);
        panel.add(btnSmallMove);
        panel.add(btnMediumMove);
        panel.add(btnLargeMove);
        formPanel.add(panelField);

        formPanel.add(LabelField.createBoldHeaderLabel("Crop options:", headerSize));

        cropColorField = new ColorField("Crop line color:", ColorSelectionType.SOLID).setColor(Color.RED);
        cropColorField.addValueChangedListener(f -> updateVisibleCrop());
        formPanel.add(cropColorField);

        List<String> options = List.of("Thin", "Medium", "Thick");
        cropLineWidthField = new ComboField<>("Crop line width:", options, 0);
        cropLineWidthField.addValueChangedListener(f -> updateVisibleCrop());
        formPanel.add(cropLineWidthField);

        formPanel.add(LabelField.createBoldHeaderLabel("Instructions:", headerSize));
        formPanel.add(new LabelField("<html>" +
                                         "Left/Right: move left border" +
                                         "<br>Up/Down: move top border" +
                                         "<br>Shift+Left/Right: move right border" +
                                         "<br>Shift+Up/Down: move bottom border" +
                                         "<br><br>Ctrl+1/2/3: movement increment" +
                                         "<br>Ctrl+Z = reset crop" +
                                         "<br><br>ESC: cancel" +
                                         "<br>Enter: save and close" +
                                         "</html>"));


        PanelField wrapper = new PanelField(new GridBagLayout());
        wrapper.setShouldExpand(true);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 0, 4, 0);
        gbc.anchor = GridBagConstraints.CENTER;
        panel = wrapper.getPanel();
        JButton button = new JButton("Reset");
        button.addActionListener(e -> resetCrop());
        button.setPreferredSize(new Dimension(150, 24));
        panel.add(button, gbc);

        gbc.gridy++;
        button = new JButton("Save and close");
        button.addActionListener(e -> saveCrop());
        button.setPreferredSize(new Dimension(150, 24));
        panel.add(button, gbc);

        gbc.gridy++;
        button = new JButton("Cancel");
        button.addActionListener(e -> dispose());
        button.setPreferredSize(new Dimension(150, 24));
        panel.add(button, gbc);

        panel.setBorder(BorderFactory.createLoweredBevelBorder());
        wrapper.getMargins().setTop(45);
        formPanel.add(wrapper);

        return formPanel;
    }

    private void loadImage() {
        try {
            originalImage = ImageUtil.loadImage(srcFile);
            imgWidth = originalImage.getWidth();
            imgHeight = originalImage.getHeight();
            int imageType = originalImage.getColorModel().hasAlpha()
                    ? BufferedImage.TYPE_INT_ARGB
                    : BufferedImage.TYPE_INT_RGB;
            dBuffer = new BufferedImage(imgWidth, imgHeight, imageType);
            updateVisibleCrop();
        }
        catch (IOException | ArrayIndexOutOfBoundsException ioe) {
            getMessageUtil().error("Error loading image: " + ioe.getMessage(), ioe);
            dispose();
        }
    }

    /**
     * Adjusting the crop is managed via a combination of arrow keys and toggle buttons to control
     * the increment. The keyboard shortcuts are as follows:
     * <ul>
     *     <li>Left/right arrows = move left crop border</li>
     *     <li>Up/down arrows = move top crop border</li>
     *     <li>Shift + left/right arrows = move right crop border</li>
     *     <li>Shift + up/down arrows = move bottom crop border</li>
     *     <li>Ctrl+Z to reset the crop to 0 (i.e. revert the crop to match the image's borders)</li>
     *     <li>Ctrl+1, Ctrl+2, Ctrl+3 to select the movement increment (small, medium, large) -
     *         this is equivalent to clicking the toggle buttons.</li>
     * </ul>
     * <P>
     *     Use the "small", "medium", and "large" toggle buttons to
     *     control the increment for these shortcuts. This allows you to fine-tune
     *     crop movements, even on higher-resolution images.
     * </P>
     * <p>
     *     And, as usual for dialogs, ESC will cancel the dialog, and Enter
     *     will prompt to save the current crop and close the dialog.
     * </p>
     */
    private void configureKeyboardShortcuts() {
        keyStrokeManager.clear();
        keyStrokeManager.registerHandler("esc", e -> dispose());
        keyStrokeManager.registerHandler("enter", e -> promptToSaveAndExit());
        keyStrokeManager.registerHandler("Ctrl+Z", e -> resetCrop());
        keyStrokeManager.registerHandler("Ctrl+1", e -> setCropMovement(SMALL_INCREMENT));
        keyStrokeManager.registerHandler("Ctrl+2", e -> setCropMovement(MEDIUM_INCREMENT));
        keyStrokeManager.registerHandler("Ctrl+3", e -> setCropMovement(LARGE_INCREMENT));

        // Regular arrow keys will move the left/top border:
        keyStrokeManager.registerHandler("left", e -> adjustLeftBorder(NEGATIVE));
        keyStrokeManager.registerHandler("right", e -> adjustLeftBorder(POSITIVE));
        keyStrokeManager.registerHandler("up", e -> adjustTopBorder(NEGATIVE));
        keyStrokeManager.registerHandler("down", e -> adjustTopBorder(POSITIVE));

        // Shift + arrow keys will move the right/bottom border:
        keyStrokeManager.registerHandler("shift+left", e -> adjustRightBorder(NEGATIVE));
        keyStrokeManager.registerHandler("shift+right", e -> adjustRightBorder(POSITIVE));
        keyStrokeManager.registerHandler("shift+up", e -> adjustBottomBorder(NEGATIVE));
        keyStrokeManager.registerHandler("shift+down", e -> adjustBottomBorder(POSITIVE));
    }

    /**
     * Moves the left crop border in the given direction by the current increment,
     * ensuring that we don't move past the right border or the edge of the image.
     *
     * @param direction One of NEGATIVE or POSITIVE, indicating the direction to move the border (left or right).
     */
    private void adjustLeftBorder(int direction) {
        int rightBorderAbs = imgWidth - cropRightField.getCurrentValue().intValue();
        int effectiveIncrement = direction * currentIncrement;
        int newValue = Math.max(cropLeftField.getCurrentValue().intValue() + effectiveIncrement, 0);
        newValue = Math.min(newValue, rightBorderAbs - 1);
        cropLeftField.setCurrentValue(newValue);
    }

    /**
     * Moves the right crop border in the given direction by the current increment,
     * ensuring that we don't move past the left border or the edge of the image.
     *
     * @param direction One of NEGATIVE or POSITIVE, indicating the direction to move the border (left or right).
     */
    private void adjustRightBorder(int direction) {
        int rightBorderAbs = imgWidth - cropRightField.getCurrentValue().intValue();
        int leftBorderAbs = cropLeftField.getCurrentValue().intValue();
        int effectiveIncrement = direction * currentIncrement;
        int newAbs = Math.min(rightBorderAbs + effectiveIncrement, imgWidth);
        newAbs = Math.max(newAbs, leftBorderAbs + 1);
        cropRightField.setCurrentValue(imgWidth - newAbs);
    }

    /**
     * Moves the top crop border in the given direction by the current increment,
     * ensuring that we don't move past the bottom border or the edge of the image.
     *
     * @param direction One of NEGATIVE or POSITIVE, indicating the direction to move the border (up or down).
     */
    private void adjustTopBorder(int direction) {
        int bottomBorderAbs = imgHeight - cropBottomField.getCurrentValue().intValue();
        int effectiveIncrement = direction * currentIncrement;
        int newValue = Math.max(cropTopField.getCurrentValue().intValue() + effectiveIncrement, 0);
        newValue = Math.min(newValue, bottomBorderAbs - 1);
        cropTopField.setCurrentValue(newValue);
    }

    /**
     * Moves the bottom crop border in the given direction by the current increment,
     * ensuring that we don't move past the top border or the edge of the image.
     *
     * @param direction One of NEGATIVE or POSITIVE, indicating the direction to move the border (up or down).
     */
    private void adjustBottomBorder(int direction) {
        int bottomBorderAbs = imgHeight - cropBottomField.getCurrentValue().intValue();
        int topBorderAbs = cropTopField.getCurrentValue().intValue();
        int effectiveIncrement = direction * currentIncrement;
        int newAbs = Math.min(bottomBorderAbs + effectiveIncrement, imgHeight);
        newAbs = Math.max(newAbs, topBorderAbs + 1);
        cropBottomField.setCurrentValue(imgHeight - newAbs);
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
        if (imagePanel != null) {
            imagePanel.dispose();
            imagePanel = null;
        }
        if (keyStrokeManager != null) {
            keyStrokeManager.dispose();
            keyStrokeManager = null;
        }
        if (originalImage != null) {
            originalImage.flush();
            originalImage = null;
        }
        if (dBuffer != null) {
            dBuffer.flush();
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
