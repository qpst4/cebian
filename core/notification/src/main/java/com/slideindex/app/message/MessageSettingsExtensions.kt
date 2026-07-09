package com.slideindex.app.message

fun MessageSettings.passesAppFilter(data: NotificationData): Boolean =
    passesAppFilter(data.packageName, data.title, data.content)
