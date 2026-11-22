package moe.shizuku.manager.accessibility

import androidx.fragment.app.Fragment
import moe.shizuku.manager.app.AppBarFragmentActivity

class AccessibilityActivity : AppBarFragmentActivity() {

    override fun createFragment(): Fragment = AccessibilityFragment()
}
