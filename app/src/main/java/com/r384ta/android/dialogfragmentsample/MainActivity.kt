package com.r384ta.android.dialogfragmentsample

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.observe
import com.r384ta.android.dialogfragmentsample.databinding.ActivityMainBinding
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    private val viewModel: MainViewModel by viewModels()
    private var disposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = DataBindingUtil.setContentView<ActivityMainBinding>(
            this, R.layout.activity_main
        )

        binding.mainButtonRxWithDialogFragment.setOnClickListener {
            val dialog = RxDialogFragment.newInstance()
            disposable?.dispose()
            disposable = dialog.result()
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ toast("$it") }, { it.printStackTrace() })
            dialog.showNow(supportFragmentManager, RxDialogFragment.TAG)
        }

        binding.mainButtonCoroutinesWithDialog.setOnClickListener {
            launch {
                val result = CoroutineDialog().show(this@MainActivity).receive()
                toast("$result")
            }
        }

        binding.mainButtonCoroutinesWithDialogFragment.setOnClickListener {
            // val dialog = CoroutineDialogFragment.newInstance()
            // dialog.showNow(supportFragmentManager, CoroutineDialogFragment.TAG)
            launch {
                val dialog = CoroutineDialogFragment.newInstance()

                val result = dialog.showNowAndWait(
                    supportFragmentManager, CoroutineDialogFragment.TAG
                )
                toast("Dialog show and wait / $result")

                // dialog.showNow(supportFragmentManager, CoroutineDialogFragment.TAG)
                // dialog.flow()
                //     .onEach { toast("$it") }
                //     .launchIn(this@MainActivity)
                //
                // dialog.showNow(supportFragmentManager, CoroutineDialogFragment.TAG)
                // val result = dialog.result().receive()
                // toast("$result")
            }
        }

        viewModel.flow()
            .onEach { toast("ViewModel with Flow / $it") }
            .launchIn(this)

        viewModel.liveData.observe(this) {
            toast("ViewModel with LiveData / $it")
        }

        binding.mainButtonViewModelWithDialogFragment.setOnClickListener {
            val dialog = ViewModelDialogFragment.newInstance()
            dialog.showNow(supportFragmentManager, ViewModelDialogFragment.TAG)
        }
    }

    override fun onDestroy() {
        cancel()
        super.onDestroy()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

class MainViewModel : ViewModel() {
    val channel = Channel<Result>()
    val liveData = MutableLiveData<Result>()

    fun flow() = channel.receiveAsFlow()
}

class RxDialogFragment : DialogFragment() {
    companion object {
        val TAG: String = RxDialogFragment::class.java.simpleName

        fun newInstance() = RxDialogFragment()
    }

    private val subject: Subject<Result> = PublishSubject.create<Result>().toSerialized()

    override fun onCreateDialog(savedInstanceState: Bundle?) =
        AlertDialog.Builder(requireActivity())
            .setTitle("Coroutine dialog fragment")
            .setMessage("This is coroutine dialog fragment")
            .setPositiveButton("OK") { _, _ -> subject.onNext(Result.SUCCESS) }
            .setNegativeButton("Cancel") { _, _ -> subject.onNext(Result.FAILURE) }
            .create()

    fun result(): Observable<Result> = subject.hide()
}

class CoroutineDialog {
    fun show(context: Context): ReceiveChannel<Result> {
        val scope = context as CoroutineScope
        val channel = Channel<Result>()

        AlertDialog.Builder(context)
            .setTitle("Coroutine dialog")
            .setMessage("This is coroutine dialog")
            .setPositiveButton("OK") { _, _ -> scope.launch { channel.send(Result.SUCCESS) } }
            .setNegativeButton("Cancel") { _, _ -> scope.launch { channel.send(Result.FAILURE) } }
            .show()

        return channel
    }
}

class CoroutineDialogFragment : DialogFragment(), CoroutineScope by MainScope() {
    companion object {
        val TAG: String = CoroutineDialogFragment::class.java.simpleName

        fun newInstance() = CoroutineDialogFragment()
    }

    private val viewModel: MainViewModel by activityViewModels()
    private val channel: Channel<Result> = Channel()
        // get() = viewModel.channel

    override fun onCreateDialog(savedInstanceState: Bundle?) =
        AlertDialog.Builder(requireActivity())
            .setTitle("Coroutine dialog fragment")
            .setMessage("This is coroutine dialog fragment")
            .setPositiveButton("OK") { _, _ -> launch { channel.send(Result.SUCCESS) } }
            .setNegativeButton("Cancel") { _, _ -> launch { channel.send(Result.FAILURE) } }
            .create()

    override fun onDestroyView() {
        cancel()
        super.onDestroyView()
    }

    fun result() = channel

    fun flow() = channel.receiveAsFlow()

    suspend fun showNowAndWait(fm: FragmentManager, tag: String): Result {
        showNow(fm, tag)
        return channel.receive()
    }
}

class ViewModelDialogFragment : DialogFragment() {
    companion object {
        val TAG: String = ViewModelDialogFragment::class.java.simpleName

        fun newInstance() = ViewModelDialogFragment()
    }

    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?) =
        AlertDialog.Builder(requireActivity())
            .setTitle("Coroutine dialog fragment")
            .setMessage("This is coroutine dialog fragment")
            .setPositiveButton("OK") { _, _ -> viewModel.liveData.value = Result.SUCCESS }
            .setNegativeButton("Cancel") { _, _ -> viewModel.liveData.value = Result.FAILURE }
            .create()
}

enum class Result {
    SUCCESS, FAILURE
}