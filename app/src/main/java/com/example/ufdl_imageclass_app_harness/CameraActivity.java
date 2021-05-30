package com.example.ufdl_imageclass_app_harness;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.otaliastudios.cameraview.CameraException;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraOptions;
import com.otaliastudios.cameraview.CameraView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class CameraActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    private BottomSheetBehavior<View> bottomSheetBehavior;
    RecyclerView predictionRecyclerView;
    private PredictionListViewModel predictionListViewModel;
    private PredictionAdapter predictionAdapter;
    private Classifier imageClassifier;
    private CameraView camera;
    private AutoCompleteTextView frameworkSpinner, modelSpinner;
    private ArrayList<String> tfliteModels, pyTorchModels;
    private String framework, model;
    private boolean analyzerEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        ConstraintLayout bottomSheet = findViewById(R.id.camera_bottom_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        camera = findViewById(R.id.camera);
        camera.setLifecycleOwner(this);
        initialiseFrameWorkSelector();

        //initialise recyclerview and view model
        predictionListViewModel = new PredictionListViewModel();
        predictionAdapter = new PredictionAdapter(Prediction.itemCallback);
        predictionRecyclerView = findViewById(R.id.predictionRecyclerView);
        predictionRecyclerView.setAdapter(predictionAdapter);
        predictionRecyclerView.setItemAnimator(null);
        startCamera();
        createModelListsFromAssets();
        initialiseBottomSheet();
    }

    /***
     * Load the available model names from the assets folder and store them into the appropriate model list depending on the model extension
     */
    public void createModelListsFromAssets() {
        pyTorchModels = new ArrayList<>();
        tfliteModels = new ArrayList<>();

        AssetManager assetManager = getAssets();
        try {
            for (String modelName : assetManager.list("")) {
                if (modelName.endsWith(".pt")) {
                    pyTorchModels.add(modelName);
                } else if (modelName.endsWith(".tflite")) {
                    tfliteModels.add(modelName);
                }
            }
            if (initialiseModelAndFramework()) {
                frameworkSpinner.setText(framework, false);
                modelSpinner.setText(model, false);
                createClassifier();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /***
     * Initialise the default model and framework
     * @return true if initialisation succeeded or false;
     */
    private boolean initialiseModelAndFramework() {
        if (model == null && framework == null) {
            if (!pyTorchModels.isEmpty()) {
                framework = "PyTorch Mobile";
                model = pyTorchModels.get(0);
                return true;
            } else if (!tfliteModels.isEmpty()) {
                framework = "TensorFlow Lite";
                model = tfliteModels.get(0);
                return true;
            }
        }
        return false;
    }

    /***
     * Starts the camera
     */
    private void startCamera() {
        camera.addCameraListener(new CameraListener() {
            @Override
            public void onCameraOpened(@NonNull CameraOptions options) {
                super.onCameraOpened(options);
            }

            @Override
            public void onCameraError(@NonNull CameraException exception) {
                super.onCameraError(exception);
            }
        });
    }

    /***
     * Adds a frame processor to the camera for classification
     */
    private void setFrameProcessor() {
        predictionListViewModel.predictionList.observe(this, predictions -> predictionAdapter.submitList(predictions));

        camera.addFrameProcessor(frame -> {
            Bitmap bitmap = null;

            if (frame.getDataClass() == byte[].class) {
                byte[] data = frame.getData();
                bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            } else if (frame.getDataClass() == Image.class) {
                Image data = frame.getData();
                bitmap = toBitmap(data);
            }

            if (bitmap != null) {
                predictionListViewModel.updateData(imageClassifier.topKPredictions(bitmap));
            }
        });
        analyzerEnabled = true;
    }

    /***
     * Clear the frame processor and remove prediction list observers
     */
    private void removeImageAnalyzer() {
        camera.clearFrameProcessors();
        predictionAdapter.submitList(null);
        predictionListViewModel.predictionList.removeObservers(this);
    }

    /***
     * converts an image object into a bitmap
     * @param image the image to convert
     * @return a bitmap of the image
     */
    private Bitmap toBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    /***
     * initialises the bottom sheet
     */
    private void initialiseBottomSheet() {
        LinearLayout settingsHeader = findViewById(R.id.bottom_sheet_header);
        ImageView arrow = findViewById(R.id.bottom_sheet_arrow);

        // set a click listener to toggle the bottom sheet state
        settingsHeader.setOnClickListener(view -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            else bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        });


        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // rotate the arrow on the bottom sheet header depending on the position of the bottom sheet
                arrow.setRotation(slideOffset * 180);
            }
        });
    }

    /***
     * Initialise the framework spinner and populates it with the list of available frameworks
     */
    private void initialiseFrameWorkSelector()
    {
        frameworkSpinner = findViewById(R.id.frameworkTextView);
        frameworkSpinner.setOnItemClickListener(this);
        modelSpinner = findViewById(R.id.modelTextView);
        modelSpinner.setOnItemClickListener(this);

        String[] frameworkArray = getResources().getStringArray(R.array.model_frameworks);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.dropdown_item, frameworkArray);
        frameworkSpinner.setAdapter(adapter);
    }

    /***
     * Method to create an image classifier instance
     */
    public void createClassifier() {
        ClassifierDetails details = ClassifierUtils.deserializeModelJSON(this, model);
        if (details != null) {
            imageClassifier = Classifier.createInstance(this, details);
            if (imageClassifier != null && !analyzerEnabled) setFrameProcessor();
        }
        else {
            imageClassifier = null;
            analyzerEnabled = false;
            removeImageAnalyzer();
        }
    }

    /***
     * Callback method to be invoked when a spinner item has been selected
     * @param parent the spinner which was selected
     * @param view the view of the item clicked within the spinner
     * @param position the position of the selected item in the spinner
     * @param id the row id of the selected item
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (parent.getAdapter().equals(frameworkSpinner.getAdapter())) {
            String selectedFramework = parent.getItemAtPosition(position).toString();

            //if the framework has changed, update the current framework & update the spinners list to show models belonging to that framework
            if(!framework.equals(selectedFramework)) {
                framework = selectedFramework;
                modelSpinner.setText("");
                updateModelSpinnerEntries(framework);
            }
        }

        //if the selected model has changed, update the current model and use it to create a new classier.
        if (parent.getAdapter().equals(modelSpinner.getAdapter())) {
            String selectedModel = parent.getItemAtPosition(position).toString();
            if (!model.equals(selectedModel)) {
                model = parent.getItemAtPosition(position).toString();
                createClassifier();
            }
        }
    }

    /***
     * Method to update the model spinner entries to display the models for the selected framework
     * @param framework the selected framework (Pytorch Mobile or TensorFlow Lite)
     */
    public void updateModelSpinnerEntries(String framework) {
        ArrayList<String> displayModels = framework.equals("PyTorch Mobile") ? pyTorchModels : tfliteModels;
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, displayModels);
        modelSpinner.setAdapter(arrayAdapter);
        if (!displayModels.isEmpty()) {
            model = displayModels.get(0);
            modelSpinner.setText(model, false);
        }
        else model = null;
        createClassifier();
    }
}