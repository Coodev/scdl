package net.rdrei.android.scdl2.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import net.rdrei.android.scdl2.ApplicationPreferences;
import net.rdrei.android.scdl2.ui.AdViewManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;

@RunWith(TestRunner.class)
public class AdViewManagerTest {

	private boolean mAdFree = false;

	@Inject
	private AdViewManager mManager;
	
	private Activity mActivity;

	@Before
	public void setUp() {
		mActivity = new Activity();

		final ApplicationPreferences preferences = new ApplicationPreferences() {
			@Override
			public boolean isAdFree() {
				return mAdFree;
			}
		};

		AbstractModule module = new AbstractModule() {
			@Override
			protected void configure() {
				bind(ApplicationPreferences.class).toInstance(preferences);
				bind(LayoutInflater.class).toInstance(new TestLayoutInflater());
			}
		};

		TestRunner.overridenInjector(this, module);
	}

	@Test
	public void doesntInjectWithAdFree() {
		mAdFree = true;
		assertThat(mManager.addToViewIfRequired(null), is(false));
	}
	
	@Test
	public void doesInjectWithoutAdFree() {
		mAdFree = false;
		final LinearLayout layout = new LinearLayout(mActivity);
		assertThat(mManager.addToViewIfRequired(layout), is(true));
	}

	private class TestLayoutInflater extends LayoutInflater {
		public TestLayoutInflater() {
			super(null);
		}
		@Override
		public View inflate(int resource, ViewGroup root, boolean attachToRoot) {
			return new View(mActivity);
		}
		
		@Override
		public LayoutInflater cloneInContext(Context newContext) {
			return null;
		}
	}
}
