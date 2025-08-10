package com.github.anrimian.musicplayer.ui.playlist_screens.choose

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.anrimian.musicplayer.Constants
import com.github.anrimian.musicplayer.R
import com.github.anrimian.musicplayer.databinding.DialogSelectPlayListBinding
import com.github.anrimian.musicplayer.di.Components
import com.github.anrimian.musicplayer.domain.models.playlist.PlayList
import com.github.anrimian.musicplayer.ui.common.dialogs.AppBottomSheetDialog
import com.github.anrimian.musicplayer.ui.common.dialogs.showConfirmDeleteDialog
import com.github.anrimian.musicplayer.ui.common.error.ErrorCommand
import com.github.anrimian.musicplayer.ui.common.format.showSnackbar
import com.github.anrimian.musicplayer.ui.common.menu.PopupMenuWindow
import com.github.anrimian.musicplayer.ui.common.toolbar.ToolbarBackgroundDrawable
import com.github.anrimian.musicplayer.ui.playlist_screens.choose.adapter.PlayListsAdapter
import com.github.anrimian.musicplayer.ui.playlist_screens.create.CreatePlayListDialogFragment
import com.github.anrimian.musicplayer.ui.playlist_screens.rename.newRenamePlaylistDialog
import com.github.anrimian.musicplayer.ui.utils.AndroidUtils
import com.github.anrimian.musicplayer.ui.utils.ViewUtils
import com.github.anrimian.musicplayer.ui.utils.applyBottomInsets
import com.github.anrimian.musicplayer.ui.utils.attrColor
import com.github.anrimian.musicplayer.ui.utils.colorFromAttr
import com.github.anrimian.musicplayer.ui.utils.fragments.safeShow
import com.github.anrimian.musicplayer.ui.utils.getColorCompat
import com.github.anrimian.musicplayer.ui.utils.getDimension
import com.github.anrimian.musicplayer.ui.utils.views.bottom_sheet.SimpleBottomSheetCallback
import com.github.anrimian.musicplayer.ui.utils.views.delegate.BoundValuesDelegate
import com.github.anrimian.musicplayer.ui.utils.views.delegate.DelegateManager
import com.github.anrimian.musicplayer.ui.utils.views.delegate.ElevationDelegate
import com.github.anrimian.musicplayer.ui.utils.views.delegate.InsetPaddingTopDelegate
import com.github.anrimian.musicplayer.ui.utils.views.delegate.ItemDrawableTopCornersDelegate
import com.github.anrimian.musicplayer.ui.utils.views.delegate.MotionLayoutDelegate
import com.github.anrimian.musicplayer.ui.utils.views.delegate.ReverseDelegate
import com.github.anrimian.musicplayer.ui.utils.views.delegate.SlideDelegate
import com.github.anrimian.musicplayer.ui.utils.views.delegate.TextColorDelegate
import com.github.anrimian.musicplayer.ui.utils.views.delegate.TintDelegate
import com.github.anrimian.musicplayer.ui.utils.views.delegate.ToolbarBackgroundStatusBarDelegate
import com.github.anrimian.musicplayer.ui.utils.views.delegate.ToolbarDrawableColorDelegate
import com.github.anrimian.musicplayer.ui.utils.views.delegate.ToolbarDrawableTopCornersDelegate
import com.github.anrimian.musicplayer.ui.utils.views.delegate.VisibilityDelegate
import com.github.anrimian.musicplayer.ui.utils.views.recycler_view.ItemDrawable
import com.google.android.material.bottomsheet.BottomSheetBehavior
import moxy.MvpBottomSheetDialogFragment
import moxy.ktx.moxyPresenter

class ChoosePlayListDialogFragment : MvpBottomSheetDialogFragment(), ChoosePlayListView {

    companion object {
        fun newInstance(extra: Bundle? = null) = ChoosePlayListDialogFragment().apply {
            arguments = Bundle().apply {
                putBundle(Constants.Arguments.EXTRA_DATA_ARG, extra)
            }
        }
    }

    private val presenter by moxyPresenter { Components.getAppComponent().choosePlayListPresenter() }

    private lateinit var binding: DialogSelectPlayListBinding

    private lateinit var adapter: PlayListsAdapter
    private lateinit var slideDelegate: SlideDelegate

    private lateinit var toolbarBackgroundDrawable: ToolbarBackgroundDrawable
    private lateinit var backgroundDrawable: ItemDrawable

    private var onCompleteListener: ((PlayList) -> Unit)? = null
    private var complexCompleteListener: ((PlayList, Bundle) -> Unit)? = null

    private var insetTop = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AppBottomSheetDialog(
            requireContext(),
            theme,
            requireContext().colorFromAttr(R.attr.listItemBackground)
        )
    }

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        binding = DialogSelectPlayListBinding.inflate(LayoutInflater.from(context))
        val view = binding.root
        dialog.setContentView(view)

        val displayMetrics = requireActivity().resources.displayMetrics
        val height = displayMetrics.heightPixels
        val heightPercent = AndroidUtils.getFloat(resources, R.dimen.choose_playlist_dialog_height)
        val minHeight = (height * heightPercent).toInt()
        view.minimumHeight = minHeight

        toolbarBackgroundDrawable = ToolbarBackgroundDrawable(
            attrColor(R.attr.listItemBackground),
            requireContext().getColorCompat(R.color.translucent_gray)
        )
        binding.motionLayout.background = toolbarBackgroundDrawable

        backgroundDrawable = ItemDrawable()
        backgroundDrawable.setColor(attrColor(R.attr.listItemBackground))
        (view.parent as View).background = backgroundDrawable

        val layoutManager = LinearLayoutManager(context)
        binding.rvChoosePlayLists.layoutManager = layoutManager
        adapter = PlayListsAdapter(
            binding.rvChoosePlayLists,
            this::onPlayListSelected,
            this::onPlaylistMenuClicked
        )
        binding.rvChoosePlayLists.adapter = adapter
        binding.rvChoosePlayLists.applyBottomInsets()

        val bottomSheetBehavior = ViewUtils.findBottomSheetBehavior(dialog)
        bottomSheetBehavior.peekHeight = minHeight
        bottomSheetBehavior.addBottomSheetCallback(SimpleBottomSheetCallback({ newState: Int ->
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                dismissAllowingStateLoss()
            }
        }, presenter::onBottomSheetSlided))
        slideDelegate = buildSlideDelegate()

        binding.ivClose.setOnClickListener { dismiss() }
        binding.ivClose.visibility = View.INVISIBLE //start state
        binding.ivCreatePlaylist.setOnClickListener { onCreatePlayListButtonClicked() }

        binding.progressStateView.onTryAgainClick { presenter.onTryAgainButtonClicked() }

        ViewCompat.setOnApplyWindowInsetsListener(binding.listContainer) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            insetTop = insets.top
            windowInsets
        }
    }

    override fun showBottomSheetSlided(slideOffset: Float) {
        binding.rvChoosePlayLists.post {
            val contentView = AndroidUtils.getContentView(activity) ?: return@post

            var usableSlideOffset = slideOffset
            val activityHeight = contentView.height - insetTop
            val viewHeight = binding.listContainer.height
            if (activityHeight > viewHeight) {
                usableSlideOffset = 0f
            }
            slideDelegate.onSlide(usableSlideOffset)
        }
    }

    override fun showEmptyList() {
        binding.progressStateView.showMessage(R.string.play_lists_on_device_not_found, false)
    }

    override fun showList() {
        binding.progressStateView.hideAll()
    }

    override fun showLoading() {
        binding.progressStateView.showProgress()
    }

    override fun showErrorState(errorCommand: ErrorCommand) {
        binding.progressStateView.showMessage(errorCommand.message, true)
    }

    override fun updateList(list: List<PlayList>) {
        adapter.submitList(list)
    }

    override fun showConfirmDeletePlayListDialog(playList: PlayList) {
        showConfirmDeleteDialog(requireContext(), playList) {
            presenter.onDeletePlayListDialogConfirmed(playList)
        }
    }

    override fun showEditPlayListNameDialog(playList: PlayList) {
        newRenamePlaylistDialog(playList.id).safeShow(childFragmentManager)
    }

    override fun showPlayListDeleteSuccess(playList: PlayList) {
        binding.listContainer.showSnackbar(getString(R.string.play_list_deleted, playList.name))
    }

    override fun showDeletePlayListError(errorCommand: ErrorCommand) {
        binding.listContainer.showSnackbar(getString(R.string.play_list_delete_error, errorCommand.message))
    }

    private fun onPlaylistMenuClicked(playList: PlayList, view: View) {
        PopupMenuWindow.showPopup(view, R.menu.play_list_short_menu) { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_change_play_list_name -> {
                    presenter.onChangePlayListNameButtonClicked(playList)
                }
                R.id.menu_delete_play_list -> {
                    presenter.onDeletePlayListButtonClicked(playList)
                }
            }
        }
    }

    fun setOnCompleteListener(onCompleteListener: (PlayList) -> Unit) {
        this.onCompleteListener = onCompleteListener
    }

    fun setComplexCompleteListener(complexCompleteListener: (PlayList, Bundle) -> Unit) {
        this.complexCompleteListener = complexCompleteListener
    }

    private fun onPlayListSelected(playList: PlayList) {
        onCompleteListener?.invoke(playList)

        complexCompleteListener?.invoke(
            playList,
            requireArguments().getBundle(Constants.Arguments.EXTRA_DATA_ARG)!!
        )

        dismissAllowingStateLoss()
    }

    private fun onCreatePlayListButtonClicked() {
        CreatePlayListDialogFragment().safeShow(childFragmentManager)
    }

    private fun buildSlideDelegate(): SlideDelegate {
        val boundDelegate: SlideDelegate = DelegateManager()
            .addDelegate(
                BoundValuesDelegate(0.85f, 1f, VisibilityDelegate(binding.ivClose))
            )
            .addDelegate(
                BoundValuesDelegate(0.5f, 0.7f, ReverseDelegate(
                    DelegateManager().apply {
                        val radius = getDimension(R.dimen.bottom_sheet_corner_size)
                        addDelegate(ToolbarDrawableTopCornersDelegate(toolbarBackgroundDrawable, radius))
                        addDelegate(ItemDrawableTopCornersDelegate(backgroundDrawable, radius))
                    }
                ))
            )
            .addDelegate(
                BoundValuesDelegate(
                    0.7f,
                    1f,
                    DelegateManager()
                        .addDelegate(MotionLayoutDelegate(binding.motionLayout))
                        .addDelegate(
                            TextColorDelegate(
                                binding.tvTitle,
                                android.R.attr.textColorPrimary,
                                R.attr.toolbarTextColorPrimary
                            )
                        )
                        .addDelegate(
                            TintDelegate(
                                binding.ivClose,
                                R.attr.buttonColorInverse,
                                R.attr.toolbarTextColorPrimary
                            )
                        )
                        .addDelegate(
                            TintDelegate(
                                binding.ivCreatePlaylist,
                                R.attr.buttonColorInverse,
                                R.attr.toolbarTextColorPrimary
                            )
                        )
                        .addDelegate(
                            ToolbarDrawableColorDelegate(
                                requireContext(),
                                toolbarBackgroundDrawable,
                                R.attr.listItemBackground,
                                R.attr.colorPrimary
                            )
                        )
                        .addDelegate(
                            BoundValuesDelegate(
                                0.8f,
                                1f,
                                ElevationDelegate(
                                    binding.motionLayout,
                                    0f,
                                    getDimension(R.dimen.toolbar_elevation)
                                )
                            )
                        )
                        //O: start expanding toolbar when leftHeight <= toolbarHeight
                        //   do not allow to stop in transition state
                        .addDelegate(
                            InsetPaddingTopDelegate(
                                binding.motionLayout,
                                0f,
                                { insetTop.toFloat() }
                            )
                        )
                        .addDelegate(
                            ToolbarBackgroundStatusBarDelegate(
                                toolbarBackgroundDrawable,
                                { insetTop.toFloat() }
                            )
                        )
                )
            )
        return BoundValuesDelegate(0.008f, 0.95f, boundDelegate)
    }

}