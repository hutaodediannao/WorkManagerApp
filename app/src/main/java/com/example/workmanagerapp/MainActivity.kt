package com.example.workmanagerapp

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.lifecycle.Observer
import androidx.work.*
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TAG = "MainActivity"

class UploadTask(context: Context, workPram: WorkerParameters) : Worker(context, workPram) {
    override fun doWork(): Result {
        val data = inputData.keyValueMap["key"]
        Thread.sleep(1000 * 3)
        Log.i(TAG, "doWork: 上传请求执行成功. 获取参数为:$data, 正在发送请求结果给下载...")
        return when (data) {
            "ok" -> Result.success(workDataOf("result" to data))
            "no" -> Result.failure(workDataOf("failed" to "error"))
            else -> Result.failure()
        }
    }
}

class DownloadTask(context: Context, workPram: WorkerParameters) : Worker(context, workPram) {
    override fun doWork(): Result {
        val param = inputData.keyValueMap["result"]
        Log.i(TAG, "doWork: 下载参数为:$param")
        return Result.success(workDataOf("end" to "下载完成!"))
    }
}

//处理异步返回结果的的任务
class MyLabelTask(context: Context, workPram: WorkerParameters) :
    ListenableWorker(context, workPram) {
    override fun startWork(): ListenableFuture<Result> {
        return CallbackToFutureAdapter.getFuture {
            //里面执行异步任务
            Executors.newSingleThreadExecutor().execute {
                Thread.sleep(3000)
                when (getCallbackResult(200)) {
                    "ok" -> it.set(Result.success(workDataOf("key" to "suc")))
                    else -> it.setException(Throwable("error"))
                }
            }

            it.addCancellationListener({
                Log.i(TAG, "startWork: 下载任务取消了...")
            }, Executors.newSingleThreadExecutor())
        }
    }

    private fun getCallbackResult(code: Int): String = when (code) {
        200 -> "ok"
        else -> "error"
    }
}

val ca = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

val firstRequest = OneTimeWorkRequestBuilder<UploadTask>()
    .setConstraints(ca)
    .setInputData(workDataOf("key" to "ok"))
    .build()
val secondRequest = OneTimeWorkRequestBuilder<DownloadTask>()
    .setConstraints(ca)
    .setInitialDelay(5, TimeUnit.SECONDS)
    .build()
val myLabelTask = OneTimeWorkRequestBuilder<MyLabelTask>()
    .setConstraints(ca)
    .build()

class MainActivity : AppCompatActivity() {

    private var tv: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tv = findViewById(R.id.textView)
    }

    fun sendRequest(view: View) {
        WorkManager.getInstance(this)
            .beginWith(firstRequest)
            .then(secondRequest)
            .then(myLabelTask)//此请求回调结果内部执行异步任务
            .enqueue()

        WorkManager.getInstance(this).getWorkInfoByIdLiveData(secondRequest.id)
            .observe(this, Observer {
                it?.apply {
                    if (state == WorkInfo.State.SUCCEEDED) {
                        Log.i(TAG, "sendRequest: 下载完成，结果为:${this.outputData.getString("end")}")
                        tv!!.text = this.outputData.getString("end")
                    }
                }
            })

    }
}