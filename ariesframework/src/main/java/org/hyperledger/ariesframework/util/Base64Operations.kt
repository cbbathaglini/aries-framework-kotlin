package org.hyperledger.ariesframework.util

import android.util.Base64

class Base64Operations {
    companion object{

         fun fromBase64ToStr(base64Str: String?): String{
            val dataDecoded = Base64.decode(base64Str, Base64.DEFAULT)
            return String(dataDecoded, Charsets.UTF_8)
        }
    }
}