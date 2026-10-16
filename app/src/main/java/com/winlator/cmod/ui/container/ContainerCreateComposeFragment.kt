package com.winlator.cmod.ui.container

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.winlator.cmod.ui.theme.WinZTheme

class ContainerCreateComposeFragment : Fragment() {
    companion object {
        private const val ARG_EDIT_CONTAINER_ID = "edit_container_id"

        @JvmStatic
        fun forEdit(containerId: Int): ContainerCreateComposeFragment = ContainerCreateComposeFragment().apply {
            arguments = Bundle().apply { putInt(ARG_EDIT_CONTAINER_ID, containerId) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        val editId = arguments?.getInt(ARG_EDIT_CONTAINER_ID, -1)?.takeIf { it > 0 }
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            WinZTheme {
                ContainerEditorV2(
                    editId = editId,
                    onBack = { close() },
                    onCreated = { close() }
                )
            }
        }
    }

    // AGVN: a container made while the app was in the background ended it at popBackStack
    // ("Can not perform this action after onSaveInstanceState"); the screen now closes when the app is back
    private var closeOnResume = false

    private fun close() {
        if (!isAdded) return
        if (parentFragmentManager.isStateSaved) closeOnResume = true else parentFragmentManager.popBackStack()
    }

    override fun onResume() {
        super.onResume()
        if (closeOnResume) {
            closeOnResume = false
            parentFragmentManager.popBackStack()
        }
    }
}
