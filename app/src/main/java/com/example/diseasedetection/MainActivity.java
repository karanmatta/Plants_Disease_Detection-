package com.example.diseasedetection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
//import android.media.Image;
//import android.media.MediaMetadataRetriever;
import android.graphics.drawable.BitmapDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
//import android.provider.MediaStore;
import android.provider.MediaStore;
import android.view.View;
//import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.diseasedetection.ml.DiseaseDetection;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MainActivity extends AppCompatActivity {
    TextView result,demoText,classified,clickhere;
    ImageView imageView,arrowImage;
    int imageSize = 224;
    ImageButton picture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        result = findViewById(R.id.result);
        imageView = findViewById(R.id.imageView);
        picture = findViewById(R.id.button);

        demoText = findViewById(R.id.demoText);
        clickhere = findViewById(R.id.clickHere);
        arrowImage = findViewById(R.id.demoArrow);
        classified = findViewById(R.id.classified);

        demoText.setVisibility(View.VISIBLE);
        clickhere.setVisibility(View.GONE);
        arrowImage.setVisibility(View.VISIBLE);
        classified.setVisibility(View.GONE);
        result.setVisibility(View.GONE);

        picture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                    requestPermissions(new String[]{android.Manifest.permission.CAMERA},1);
                }
                else{
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(intent, 100);
                }
            }
        });

        // Add the following code to load the image from the camera
        if (savedInstanceState != null) {
            Bitmap imageBitmap = savedInstanceState.getParcelable(IMAGE_BITMAP_KEY);
            if (imageBitmap != null) {
                imageView.setImageBitmap(imageBitmap);
            }
        }
    }
    private static final String IMAGE_BITMAP_KEY = "imageBitmap";

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save the image bitmap to the bundle
        BitmapDrawable drawable = (BitmapDrawable) imageView.getDrawable();
        if (drawable != null) {
            Bitmap imageBitmap = drawable.getBitmap();
            outState.putParcelable(IMAGE_BITMAP_KEY, imageBitmap);
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 100){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(cameraIntent, 1);
            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(requestCode == 100 && resultCode == RESULT_OK){
            Bitmap image = (Bitmap) data.getExtras().get("data");
            int dimension = Math.min(image.getWidth(),image.getHeight());
            image = ThumbnailUtils.extractThumbnail(image,dimension,dimension);

            // set the bitmap to the ImageView
            imageView.setImageBitmap(image);

            demoText.setVisibility(View.GONE);
            clickhere.setVisibility(View.VISIBLE);
            arrowImage.setVisibility(View.GONE);
            classified.setVisibility(View.VISIBLE);
            result.setVisibility(View.VISIBLE);

            image = Bitmap.createScaledBitmap(image,imageSize,imageSize,false);
            classifyImage(image);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }



    private void classifyImage(Bitmap image) {
        try {
            DiseaseDetection model = DiseaseDetection.newInstance(getApplicationContext());

            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4* imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            // Get ID of array of 224*224 pixel in image
            int[] intValues = new int[imageSize * imageSize];
            image.getPixels(intValues, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
            // Iterate over pixel and extract R,G,B values

            int pixel =0;
            for (int i = 0; i < imageSize; ++i) {
                for (int j = 0; j < imageSize; ++j) {
                    int val = intValues[pixel++];// RGB
                    byteBuffer.putFloat((((val >> 16) & 0xFF)* (1.f/255.f)));
                    byteBuffer.putFloat((((val >> 8) & 0xFF)* (1.f/255.f)));
                    byteBuffer.putFloat((val & 0xFF)* (1.f/255.f));


                }
            }
            inputFeature0.loadBuffer(byteBuffer);

            // run model interface and gets result

            DiseaseDetection.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            float[] confidence = outputFeature0.getFloatArray();

            // find the index of class with maximum confidence

            int maxPos = 0;
            float maxConfidence = confidence[0];
            for(int i=1;i<confidence.length;i++){
                if(confidence[i]>maxConfidence){
                    maxPos = i;
                    maxConfidence = confidence[i];
                }
            }
            String[] classes = {"Apple black rot" , "Corn gray leaf spot" ," Grape black rot", "Orange haunglongbing", "Peach bacterial spot", "Potato early blight",  "Strawberry leaf scorch" };

            result.setText(classes[maxPos]);
            result.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/search?q="+ result.getText().toString())));
                }
            });

            model.close();
        }

        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}