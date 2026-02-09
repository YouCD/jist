package dev.rcht.jist.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import dev.rcht.jist.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val aboutViewModel = ViewModelProvider(this).get(AboutViewModel::class.java)

        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        val root: View = binding.root

        aboutViewModel.appVersion.observe(viewLifecycleOwner) {
            binding.versionText.text = it
        }

        aboutViewModel.description.observe(viewLifecycleOwner) {
            binding.descriptionText.text = it
        }

        aboutViewModel.buildInfo.observe(viewLifecycleOwner) {
            binding.buildInfoText.text = it
        }

        binding.githubLinkText.setOnClickListener {
            val url = "https://github.com/rchtgzm/jist"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
