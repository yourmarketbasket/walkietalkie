package com.example.walkietalkieapp.model

data class Device(
    val name: String,
    val address: String,
    val type: String,
    val status: String? = null
)
