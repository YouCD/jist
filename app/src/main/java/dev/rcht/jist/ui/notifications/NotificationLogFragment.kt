package dev.rcht.jist.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import dev.rcht.jist.databinding.FragmentNotificationLogBinding

class NotificationLogFragment : Fragment() {

    private var _binding: FragmentNotificationLogBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: NotificationLogViewModel
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this).get(NotificationLogViewModel::class.java)

        setupRecyclerView()
        setupSearchView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter()
        val recyclerView = binding.notificationRecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
        })
    }

    private fun setupObservers() {
        viewModel.filteredNotifications.observe(viewLifecycleOwner) { notifications ->
            adapter.submitList(notifications)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
