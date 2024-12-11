package com.example.cc.ui.ride_history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class RideHistoryViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is Ride History Fragment"
    }
    val text: LiveData<String> = _text
}