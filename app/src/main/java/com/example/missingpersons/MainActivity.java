package com.example.missingpersons;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    //constants
    private int OUTPUT_SIZE = 192;
    private static final int MY_CAMERA_REQUEST_CODE = 100;
    private static int SELECT_PICTURE = 1;
    private int inputSize = 112;
    private float IMAGE_MEAN = 128.0f;
    private float IMAGE_STD = 128.0f;

    //ui
    private Button loadFaceButton, optionsButton, cameraReverseButton, recognizeButton;
    private ImageView faceImageView;
    private TextView nameTextView, infoTextView, adminWelcomeTextView;
    private PreviewView cameraPreviewView;

    //logic
    private String tflitemodel = "mobile_face_net.tflite";
    private HashMap<Person, SimilarityClassifier.Recognition> savedFacesMap = new HashMap<>();
    private float distance = 1.0f;
    private ProcessCameraProvider processCameraProvider;
    private int cam_face = CameraSelector.LENS_FACING_BACK;
    boolean start = true, flipX = false, recognize = false, showDetails = true;
    private FaceDetector faceDetector;
    private Interpreter tensorflowInterpreter;
    private int[] intValues;
    private float[][] embeddings;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private CameraSelector cameraSelector;
    private Helper helper;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        helper = new Helper();

        Intent intent = getIntent();
        String activity = intent.getStringExtra("activity");

        initUI(activity);
        initOnClick(activity);
        checkCameraPermissions();

        savedFacesMap = readSharedPreferences();

        try {
            tensorflowInterpreter = new Interpreter(helper.loadModelFile(MainActivity.this, tflitemodel));
        } catch (IOException e) {
            e.printStackTrace();
        }

        FaceDetectorOptions faceDetectorOptions =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build();
        faceDetector = FaceDetection.getClient(faceDetectorOptions);

        cameraBind();
    }

    private void initOnClick(String activity) {
        loadFaceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadPhoto();
            }
        });

        recognizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                start = true;
                recognize = !recognize;
                if(recognize){
                    recognizeButton.setText("Recognizing...");
                } else{
                    recognizeButton.setText("Recognize");
                }
            }
        });

        optionsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Options:");

                String[] names = {"View Persons List", "Update Persons List", "Save Persons List", "Load Persons List", "Clear All Persons"};

                builder.setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        switch (which) {
                            case 0:
                                displayPersonsList();
                                break;
                            case 1:
                                if (activity.equals("splash")) {
                                    Toast.makeText(MainActivity.this, "Please log in as admin for this option!", Toast.LENGTH_SHORT).show();
                                    break;
                                }
                                updatePersonsList();
                                break;
                            case 2:
                                if(activity.equals("splash")){
                                    Toast.makeText(MainActivity.this, "Please log in as admin for this option!", Toast.LENGTH_SHORT).show();
                                    break;
                                }
                                writeSharedPreferences(savedFacesMap, 0); //mode: 0:save all, 1:clear all, 2:update all
                                Toast.makeText(MainActivity.this, "Saved faces!", Toast.LENGTH_SHORT).show();
                                break;
                            case 3:
                                //savedFacesMap.putAll(readSharedPreferences());
                                //no need for this since latest faces are automatically loaded when activity starts
                                Toast.makeText(MainActivity.this, "Latest faces loaded from storage!", Toast.LENGTH_SHORT).show();
                                break;
                            case 4:
                                if (activity.equals("splash")) {
                                    Toast.makeText(MainActivity.this, "Please log in as admin for this option!", Toast.LENGTH_SHORT).show();
                                    break;
                                }
                                clearPersonsList();
                                break;
                        }
                    }
                });

                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                builder.setNegativeButton("Cancel", null);

                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

        //toggle between front and back cameras
        cameraReverseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cam_face == CameraSelector.LENS_FACING_BACK) {
                    cam_face = CameraSelector.LENS_FACING_FRONT;
                    flipX = true;
                } else {
                    cam_face = CameraSelector.LENS_FACING_BACK;
                    flipX = false;
                }
                processCameraProvider.unbindAll();
                cameraBind();
            }
        });
    }

    //bind camera and previewview
    private void cameraBind() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                processCameraProvider = cameraProviderFuture.get();
                bindPreview(processCameraProvider);
            } catch (ExecutionException | InterruptedException e) {
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(cam_face)
                .build();

        //show latest frame in camera
        preview.setSurfaceProvider(cameraPreviewView.getSurfaceProvider());
        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

        Executor executor = Executors.newSingleThreadExecutor();
        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                try {
                    Thread.sleep(0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                //ml kit object
                InputImage image = null;

                @SuppressLint("UnsafeExperimentalUsageError")

                //android object
                Image mediaImage = imageProxy.getImage();

                //convert for face detector
                if (mediaImage != null) {
                    image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                }

                //process to detect face in image
                Task<List<Face>> result =
                        faceDetector.process(image).addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                            @Override
                            public void onSuccess(List<Face> faces) {
                                //if there are some saved faces
                                if (faces.size() != 0) {
                                    //get first detected face from input image
                                    Face face = faces.get(0);

                                    //convert image to bitmap
                                    Bitmap frame_bmp = helper.toBitmap(mediaImage);
                                    int rot = imageProxy.getImageInfo().getRotationDegrees();

                                    //rotate bitmap (why?)
                                    Bitmap frame_bmp1 = helper.rotateBitmap(frame_bmp, rot, false, false);

                                    //crop image to get only face (small box)
                                    RectF boundingBox = new RectF(face.getBoundingBox());
                                    Bitmap cropped_face = helper.getCropBitmapByCPU(frame_bmp1, boundingBox);

                                    if (flipX)
                                        cropped_face = helper.rotateBitmap(cropped_face, 0, flipX, false);

                                    //resize to required dimensions
                                    Bitmap scaled = helper.getResizedBitmap(cropped_face, 112, 112);

                                    //if recognize button has been clicked
                                    if (start) {
                                        //put image in model and get closest faces to get name
                                        recognizeImage(scaled);
                                    }
                                } else {
                                    if (savedFacesMap.isEmpty()) {
                                        nameTextView.setText("No face in storage yet!");
                                    } else {
                                        if(recognize){
                                            nameTextView.setText("No face detected!");
                                        } else{
                                            nameTextView.setText("Please recognize to scan");
                                        }
                                    }
                                }
                            }
                        })
                                .addOnFailureListener(
                                        new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                            }
                                        })
                                .addOnCompleteListener(new OnCompleteListener<List<Face>>() {
                                    @Override
                                    public void onComplete(@NonNull Task<List<Face>> task) {
                                        imageProxy.close();
                                    }
                                });
            }
        });

        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageAnalysis, preview);
    }

    private void clearPersonsList() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Do you want to delete all Persons?");

        builder.setPositiveButton("Delete All", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                savedFacesMap.clear();
                Toast.makeText(MainActivity.this, "Persons Cleared", Toast.LENGTH_SHORT).show();
            }
        });

        //update sharedprefs
        writeSharedPreferences(savedFacesMap, 1);
        builder.setNegativeButton("Cancel", null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void updatePersonsList() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

        //if no faces saved
        if (savedFacesMap.isEmpty()) {
            builder.setTitle("No Person Added!!");
            builder.setPositiveButton("OK", null);
        } else {
            builder.setTitle("Select person to delete:");

            //show list of saved names
            String[] names = new String[savedFacesMap.size()];
            boolean[] checkedItems = new boolean[savedFacesMap.size()];
            int i = 0;
            for (Map.Entry<Person, SimilarityClassifier.Recognition> entry : savedFacesMap.entrySet()) {
                names[i] = entry.getKey().getName();
                checkedItems[i] = false;
                i = i + 1;
            }

            builder.setMultiChoiceItems(names, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                    checkedItems[which] = isChecked;
                }
            });


            //delete selected faces
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    for (int i = 0; i < checkedItems.length; i++) {
                        if (checkedItems[i]) {
                            for (Map.Entry<Person, SimilarityClassifier.Recognition> entry : savedFacesMap.entrySet()) {
                                if(entry.getKey().getName().equals(names[i])){
                                    savedFacesMap.remove(entry.getKey());
                                }
                            }
                        }
                    }

                    //save updated list to shared prefs
                    writeSharedPreferences(savedFacesMap, 2);
                    Toast.makeText(MainActivity.this, "Persons List Updated", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Cancel", null);

            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    private void displayPersonsList() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

        //if no faces have been added yet
        if (savedFacesMap.isEmpty())
            builder.setTitle("No Person Added!!");
        else
            builder.setTitle("Persons:");

        //add list of faces that are stored
        String[] names = new String[savedFacesMap.size()];
        boolean[] checkedItems = new boolean[savedFacesMap.size()];
        int i = 0;
        for (Map.Entry<Person, SimilarityClassifier.Recognition> entry : savedFacesMap.entrySet()) {
            names[i] = entry.getKey().getName();
            checkedItems[i] = false;
            i = i + 1;
        }
        builder.setItems(names, null);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void loadPhoto() {
        start = false;
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkCameraPermissions() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initUI(String activity) {
        loadFaceButton = findViewById(R.id.button_load);
        optionsButton = findViewById(R.id.button_options);
        cameraReverseButton = findViewById(R.id.button_camera_reverse);
        recognizeButton = findViewById(R.id.button_recognize);
        faceImageView = findViewById(R.id.imageView_face);
        nameTextView = findViewById(R.id.textView_name);
        cameraPreviewView = findViewById(R.id.previewView_camera);
        infoTextView = findViewById(R.id.textView_info);
        adminWelcomeTextView = findViewById(R.id.textView_admin_welcome);

        //came from splash activity
        if (activity.equals("splash")) {
            loadFaceButton.setVisibility(View.GONE);
            adminWelcomeTextView.setVisibility(View.GONE);
            infoTextView.setText("Click on identified person's name for more details");
        } else if (activity.equals("login")) { //came from admin login
            recognizeButton.setVisibility(View.GONE);
            nameTextView.setVisibility(View.GONE);
            cameraPreviewView.setVisibility(View.GONE);
            cameraReverseButton.setVisibility(View.GONE);
            FrameLayout container = findViewById(R.id.container_camera);
            container.setVisibility(View.GONE);
            infoTextView.setText("Please save the list before exiting");
        }
    }

    //load faces from shared prefs
    private HashMap<Person, SimilarityClassifier.Recognition> readSharedPreferences() {
        //get value from shared prefs
        SharedPreferences sharedPreferences = getSharedPreferences("myprefs", MODE_PRIVATE);
        String defNames = new Gson().toJson(new HashMap<String, SimilarityClassifier.Recognition>());
        String defPersons = new Gson().toJson(new HashMap<String, Person>());

        String namesJSON = sharedPreferences.getString("names", defNames);
        String personsJSON = sharedPreferences.getString("persons", defPersons);

        TypeToken<HashMap<String, SimilarityClassifier.Recognition>> tokenName = new TypeToken<HashMap<String, SimilarityClassifier.Recognition>>() {
        };
        HashMap<String, SimilarityClassifier.Recognition> namesMap = new Gson().fromJson(namesJSON, tokenName.getType());
        TypeToken<HashMap<String, Person>> tokenPerson = new TypeToken<HashMap<String, Person>>() {
        };
        HashMap<String, Person> personsMap = new Gson().fromJson(personsJSON, tokenPerson.getType());

        HashMap<Person, SimilarityClassifier.Recognition> retrievedMap = new HashMap<>();

        for (Map.Entry<String, SimilarityClassifier.Recognition> names : namesMap.entrySet()) {
            //embeddings
            float[][] output = new float[1][OUTPUT_SIZE];
            ArrayList arrayList = (ArrayList) names.getValue().getExtra();
            arrayList = (ArrayList) arrayList.get(0);
            for (int counter = 0; counter < arrayList.size(); counter++) {
                output[0][counter] = ((Double) arrayList.get(counter)).floatValue();
            }
            names.getValue().setExtra(output);

            for(Map.Entry<String, Person> person : personsMap.entrySet()){
                if(names.getKey().equals(person.getKey())){
                    //Log.d("MAINACTIVITY", "."+person.getValue().getName());
                    retrievedMap.put(person.getValue(), names.getValue());
                }
            }
        }

        //Toast.makeText(MainActivity.this, "Loaded faces", Toast.LENGTH_SHORT).show();
        return retrievedMap;
    }

    //save faces into shared prefs
    private void writeSharedPreferences(HashMap<Person, SimilarityClassifier.Recognition> jsonMap, int mode) {
        //0- save all
        //1- clear all
        //2- update
        if (mode == 0) {
            jsonMap.putAll(readSharedPreferences());
        } else if (mode == 1) {
            jsonMap.clear();
        }

        HashMap<String, SimilarityClassifier.Recognition> names = new HashMap<>();
        HashMap<String, Person> persons = new HashMap<>();
        for (Map.Entry<Person, SimilarityClassifier.Recognition> entry : jsonMap.entrySet()) {
            names.put(entry.getKey().getName(), entry.getValue());
            //Log.d("MAINACTIVITY", entry.getKey().getName());
            persons.put(entry.getKey().getName(), entry.getKey());
        }

        String namesJSON = new Gson().toJson(names);
        String personsJSON = new Gson().toJson(persons);

        SharedPreferences sharedPreferences = getSharedPreferences("myprefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("names", namesJSON);
        editor.putString("persons", personsJSON);
        editor.apply();
        //Toast.makeText(MainActivity.this, "Saved faces", Toast.LENGTH_SHORT).show();
    }

    //after loading image
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {

                //image from gallery
                Uri selectedImageUri = data.getData();
                try {
                    //convert uri to inputimage
                    InputImage impphoto = InputImage.fromBitmap(helper.getBitmapFromUri(selectedImageUri, MainActivity.this), 0);

                    //process image
                    faceDetector.process(impphoto).addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                        @Override
                        public void onSuccess(List<Face> faces) {

                            //if there are some saved faces
                            if (faces.size() != 0) {

                                //get first detected face from input image
                                Face face = faces.get(0);

                                //convert uri to bitmap
                                Bitmap frame_bmp = null;
                                try {
                                    frame_bmp = helper.getBitmapFromUri(selectedImageUri, MainActivity.this);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                //rotate bitmap (why?)
                                Bitmap frame_bmp1 = helper.rotateBitmap(frame_bmp, 0, flipX, false);

                                //crop image to get only face (small box)
                                RectF boundingBox = new RectF(face.getBoundingBox());
                                Bitmap cropped_face = helper.getCropBitmapByCPU(frame_bmp1, boundingBox);

                                //resize to required dimensions
                                Bitmap scaled = helper.getResizedBitmap(cropped_face, 112, 112);

                                //put image in model and get closest faces to get name
                                recognizeImage(scaled);

                                //add name for face
                                addFace();

                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            start = true;
                            Toast.makeText(MainActivity.this, "Failed to add", Toast.LENGTH_SHORT).show();
                        }
                    });
                    faceImageView.setImageBitmap(helper.getBitmapFromUri(selectedImageUri, MainActivity.this));
                } catch (IOException e) {
                    e.printStackTrace();
                }


            }
        }
    }

    private void addFace() {
        start = false;
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        AlertDialog dialog = builder.create();

        //take person details input
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_details, null);
        builder.setView(dialogView);

        EditText nameEditText = dialogView.findViewById(R.id.edittext_name);
        EditText ageEditText = dialogView.findViewById(R.id.edittext_age);
        EditText lastLocationEditText = dialogView.findViewById(R.id.edittext_last_location);
        EditText lastClothesEditText = dialogView.findViewById(R.id.edittext_last_clothes);
        EditText contactPhoneEditText = dialogView.findViewById(R.id.edittext_contact_phone);

        builder.setPositiveButton("ADD", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //create and initialize new object with face embeddings and name
                //embeddings have been updated during recognizeImage()
                SimilarityClassifier.Recognition result = new SimilarityClassifier.Recognition(
                        "0", "", -1f);
                result.setExtra(embeddings);

                String name = nameEditText.getText().toString();
                String ageString = ageEditText.getText().toString();
                int age = Integer.parseInt(ageString);
                String lastLocation = lastLocationEditText.getText().toString();
                String lastClothes = lastClothesEditText.getText().toString();
                String contactPhone = contactPhoneEditText.getText().toString();

                if(name.equals("") || ageString.equals("") || lastLocation.equals("") || lastClothes.equals("") || contactPhone.equals("")){
                    Toast.makeText(MainActivity.this, "Please fill all fields!", Toast.LENGTH_SHORT).show();
                    return;
                }

                Person person = new Person(name, age, lastLocation, lastClothes, contactPhone);

                savedFacesMap.put(person, result);
                start = true;
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                start = true;
                dialog.cancel();
            }
        });

        builder.show();
    }

    public void recognizeImage(final Bitmap bitmap) {
        //show small block of image in preview
        faceImageView.setImageBitmap(bitmap);

        //create bytebuffer
        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4);
        imgData.order(ByteOrder.nativeOrder());
        intValues = new int[inputSize * inputSize];

        //get pixel values from bitmap to normalize
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        //what is this
        imgData.rewind();

        //put face float values in bytebuffer
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                // Float model
                imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }

        //imgData is input to our model
        Object[] inputArray = {imgData};

        Map<Integer, Object> outputMap = new HashMap<>();

        //embeddings is output (1d array of float/list of float)
        embeddings = new float[1][OUTPUT_SIZE];
        outputMap.put(0, embeddings);

        //run model
        tensorflowInterpreter.runForMultipleInputsOutputs(inputArray, outputMap);

        float distance_local = Float.MAX_VALUE;

        //compare new face with saved
        if (savedFacesMap.size() > 0) {
            //pair<name, distance>, get 2 closest matching in list
            final List<Pair<String, Float>> nearest = findNearest(embeddings[0]);

            if (nearest.get(0) != null) {
                //closest matching face
                final String name = nearest.get(0).first;
                distance_local = nearest.get(0).second;

                //if distance between closest face is more than 1.000 ,then output UNKNOWN face.
                if (distance_local < distance) {
                    if(recognize){
                        for (Map.Entry<Person, SimilarityClassifier.Recognition> entry : savedFacesMap.entrySet()){
                            if(entry.getKey().getName().equals(name)){
                                nameTextView.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                                        View dialogView = getLayoutInflater().inflate(R.layout.dialog_person, null);
                                        builder.setView(dialogView);

                                        TextView nameTV = dialogView.findViewById(R.id.textView_name_details);
                                        TextView ageTV = dialogView.findViewById(R.id.textView_age_details);
                                        TextView lastLocationTV = dialogView.findViewById(R.id.textView_last_location_details);;
                                        TextView lastClothesTV = dialogView.findViewById(R.id.textView_last_clothes_details);
                                        TextView contactPhoneTV = dialogView.findViewById(R.id.textView_contact_phone_details);

                                        nameTV.setText("Name: "+entry.getKey().getName());
                                        ageTV.setText("Age: "+entry.getKey().getAge());
                                        lastLocationTV.setText("Last known location: "+entry.getKey().getLastLocation());
                                        lastClothesTV.setText("Clothes last seen wearing: "+entry.getKey().getLastClothes());
                                        contactPhoneTV.setText("Contact: "+entry.getKey().getContactPhone());

                                        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {

                                            }
                                        });

                                        AlertDialog dialog = builder.create();
                                        dialog.show();
                                    }
                                });
                            }
                        }

                        nameTextView.setText(name);
                    }
                } else {
                    if(recognize){
                        nameTextView.setText("Unknown");
                    }
                }
            }

        }
    }

    //compare faces by distance between face embeddings
    private List<Pair<String, Float>> findNearest(float[] emb) {
        List<Pair<String, Float>> neighbour_list = new ArrayList<Pair<String, Float>>();
        Pair<String, Float> ret = null; //to get closest match
        Pair<String, Float> prev_ret = null; //to get second closest match
        for (Map.Entry<Person, SimilarityClassifier.Recognition> entry : savedFacesMap.entrySet()) {
            final String name = entry.getKey().getName();
            final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];

            float distance = 0;
            for (int i = 0; i < emb.length; i++) {
                float diff = emb[i] - knownEmb[i];
                distance += diff * diff;
            }
            distance = (float) Math.sqrt(distance);

            //what is this
            if (ret == null || distance < ret.second) {
                prev_ret = ret;
                ret = new Pair<>(name, distance);
            }
        }

        if (prev_ret == null) prev_ret = ret;
        neighbour_list.add(ret);
        neighbour_list.add(prev_ret);

        return neighbour_list;
    }
}
