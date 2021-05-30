package com.example.ufdl_imageclass_app_harness;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.util.List;

/***
 * View Model class designed to store and manage a live data prediction list which will be used to update the prediction recyclerview in the camera fragment.
 */

public class PredictionListViewModel extends ViewModel {
    private final MutableLiveData<List<Prediction>> _predictionList = new MutableLiveData<>();
    LiveData<List<Prediction>> predictionList = _predictionList;

    /***
     * Method to update the prediction list data
     * @param predictions a new updated list of predictions
     */
    public void updateData(List<Prediction> predictions) {
        _predictionList.postValue(predictions);
    }
}
