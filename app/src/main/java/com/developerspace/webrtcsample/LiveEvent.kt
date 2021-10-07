package com.developerspace.webrtcsample

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class LiveEvent(
    val action: Int,
    val x: Float, val y: Float
) : Parcelable
