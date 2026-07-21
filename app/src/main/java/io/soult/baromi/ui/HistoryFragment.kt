package io.soult.baromi.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.databinding.FragmentHistoryBinding
import kotlinx.coroutines.launch

/** The journal (History tab): your check-ins, newest first. */
class HistoryFragment : Fragment() {

    private var binding: FragmentHistoryBinding? = null
    private val adapter = CheckinAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentHistoryBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        b.historyList.layoutManager = LinearLayoutManager(requireContext())
        b.historyList.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            val db = BaromiDatabase.getInstance(requireContext().applicationContext)
            val items = db.checkinDao().getAllWithDetails()
            val categories = db.categoryDao().getAll()
            adapter.submit(items, categories)
            val empty = items.isEmpty()
            b.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
            b.historyList.visibility = if (empty) View.GONE else View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
