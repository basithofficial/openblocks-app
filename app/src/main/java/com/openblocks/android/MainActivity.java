package com.openblocks.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.openblocks.android.fragments.main.ModulesFragment;
import com.openblocks.android.fragments.main.ProjectsFragment;
import com.openblocks.android.modman.ModuleJsonCorruptedException;
import com.openblocks.android.modman.ModuleLoader;
import com.openblocks.android.modman.ModuleManager;
import com.openblocks.android.modman.models.Module;
import com.openblocks.moduleinterface.OpenBlocksModule;
import com.openblocks.moduleinterface.models.OpenBlocksProjectMetadata;
import com.openblocks.moduleinterface.models.OpenBlocksRawProject;
import com.openblocks.moduleinterface.models.config.OpenBlocksConfig;
import com.openblocks.moduleinterface.projectfiles.OpenBlocksCode;
import com.openblocks.moduleinterface.projectfiles.OpenBlocksLayout;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout _drawer;

    private FloatingActionButton fabProjects;
    private FloatingActionButton fabModules;

    private HashMap<OpenBlocksModule.Type, ArrayList<Module>> modules;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Main Part (ActionBar)
        Toolbar _actionBar = findViewById(R.id.toolBar);
        setSupportActionBar(_actionBar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        // Drawer Toggle & Drawer
        _drawer = findViewById(R.id.drawer_layout);
        NavigationView _drawer_navView = findViewById(R.id.nav_view);

        ActionBarDrawerToggle _toggle = new ActionBarDrawerToggle(MainActivity.this, _drawer, _actionBar, R.string.app_name, R.string.app_name);
        _drawer.addDrawerListener(_toggle);
        _toggle.syncState();

        _drawer_navView.setNavigationItemSelectedListener(this);

        // Load Modules ============================================================================

        // Get the SharedPreferences
        SharedPreferences sp = getSharedPreferences("data", MODE_PRIVATE);

        // Check if this is the first time the user has opened this app
        if (sp.getBoolean("first_time", false)) {
            // Oo, first time huh, let's initialize the modules folder, and extract our default modules there
            try {
                // Initialize the modules folder
                File modules_folder = new File(getFilesDir(), "/modules/");
                if (!modules_folder.mkdir()) {
                    throw new IOException("An unknown error occurred whilst trying to initialize the modules folder");
                }

                // Initialize the modules.json file
                if (!new File(modules_folder, "modules.json").createNewFile()) {
                    throw new IOException("An unknown error occurred whilst trying to initialize modules.json");
                }

                // TODO: EXTRACT / DOWNLOAD DEFAULT MODULES
            } catch (IOException e) {
                Toast.makeText(this, "Error while initializing modules: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        }

        // TODO: SHOW A LOADING BAR / SCREEN WHEN WE'RE LOADING MODULES
        ModuleManager moduleManager = ModuleManager.getInstance();

        // Load modules
        try {
            moduleManager.fetchAllModules(this);

        } catch (IOException e) {
            e.printStackTrace();

            Toast.makeText(this, "Error while reading modules: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (ModuleJsonCorruptedException e) {
            e.printStackTrace();

            Toast.makeText(this, "modules.json is corrupted: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        modules = moduleManager.getModules();

        // Load Modules ============================================================================

        // Load Projects ===========================================================================

        Module project_manager_module = moduleManager.getActiveModule(OpenBlocksModule.Type.PROJECT_MANAGER);
        Module project_parser_module = moduleManager.getActiveModule(OpenBlocksModule.Type.PROJECT_PARSER);
        OpenBlocksModule.ProjectManager projectManager = ModuleLoader.load(this, project_manager_module, OpenBlocksModule.ProjectManager.class);
        OpenBlocksModule.ProjectParser projectParser = ModuleLoader.load(this, project_parser_module, OpenBlocksModule.ProjectParser.class);

        ArrayList<OpenBlocksProjectMetadata> projects_metadata = new ArrayList<>();

        if (projectManager != null && projectParser != null) {
            // Only run these if the modules are successfully loaded
            ArrayList<OpenBlocksRawProject> projects = projectManager.listProjects();

            for (OpenBlocksRawProject project : projects) {
                projects_metadata.add(projectParser.parseMetadata(project));
            }
        }

        // Load Projects ===========================================================================

        // View Pager
        ViewPager viewPager = findViewById(R.id.viewPager);
        TabLayout tabLayout = findViewById(R.id.tabLayout);

        viewPager.setAdapter(new FragmentAdapter(getApplicationContext(), getSupportFragmentManager(), 2, modules, projects_metadata));
        tabLayout.setupWithViewPager(viewPager);

        // FABs
        fabProjects = findViewById(R.id.fabProjects);
        fabModules = findViewById(R.id.fabModules);

        fabModules.hide();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            fabProjects.setTooltipText("New Project");
            fabModules.setTooltipText("Add Module");
        }

        // Listeners
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int _position, float _positionOffset, int _positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int _position) {
                if (_position == 0) {
                    fabProjects.show();
                    fabModules.hide();
                } else {
                    fabProjects.hide();
                    fabModules.show();
                }
            }

            @Override
            public void onPageScrollStateChanged(int _scrollState) {

            }
        });
    }

    final int IMPORT_MODULE_REQUEST_CODE = 1;

    // When user clicked the "import" button
    public void fabModulesClicked(View view) {
        // Use SAF to pick a zip file, we ain't messing around with scoped storage
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip");

        startActivityForResult(intent, IMPORT_MODULE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_CANCELED || data == null)
            return;

        if (requestCode == IMPORT_MODULE_REQUEST_CODE) {
            // Get the URI
            Uri uri = data.getData();
            Module module;

            // Then import the module
            try {
                module = ModuleManager.getInstance().importModule(this, uri.getPath());
            } catch (IOException e) {
                Toast.makeText(this, "Error while reading module: " + e.getMessage(), Toast.LENGTH_LONG).show();

                return;
            } catch (JSONException e) {
                Toast.makeText(this, "Module is corrupted: " + e.getMessage(), Toast.LENGTH_LONG).show();

                return;
            }

            Toast.makeText(this, "Module " + module.name + " has successfully imported, restarting activity", Toast.LENGTH_SHORT).show();

            // Ok then refresh our activity
            recreate();
        }
    }


    @Override
    public void onBackPressed() {
        if (_drawer.isDrawerOpen(GravityCompat.START)) {
            _drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Intent intent = new Intent();

        int itemId = item.getItemId();

        if (itemId == R.id.home) {
            _drawer.closeDrawer(GravityCompat.START);
            return false;

        } else if (itemId == R.id.settings) {
            intent.setClass(MainActivity.this, SettingsActivity.class);

        } else if (itemId == R.id.about) {
            intent.setClass(MainActivity.this, AboutActivity.class);

        } else if (itemId == R.id.dc) {
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://discord.gg/ESCfUBy26Z"));

        } else if (itemId == R.id.gh) {
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://github.com/OpenBlocksTeam"));

        } else if (itemId == R.id.web) {
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://openblocks.tk/"));
        }

        startActivity(intent);

        return false;
    }

    public static class FragmentAdapter extends FragmentStatePagerAdapter {
        Context context;
        int tabCount;

        HashMap<OpenBlocksModule.Type, ArrayList<Module>> modules;
        ArrayList<OpenBlocksProjectMetadata> projectMetadataArrayList;

        public FragmentAdapter(Context context, FragmentManager fm, int tabCount, HashMap<OpenBlocksModule.Type, ArrayList<Module>> modules, ArrayList<OpenBlocksProjectMetadata> projectMetadataArrayList) {
            super(fm);
            this.context = context;
            this.tabCount = tabCount;
            this.modules = modules;
            this.projectMetadataArrayList = projectMetadataArrayList;
        }

        @Override
        public int getCount(){
            return tabCount;
        }

        @Override
        public CharSequence getPageTitle(int _position) {
            switch (_position) {
                case 0:
                    return "Projects";
                case 1:
                    return "Modules";
                default:
                    return null;
            }
        }

        @NonNull
        @Override
        public Fragment getItem(int _position) {
            switch (_position) {
                case 0:
                    return ProjectsFragment.newInstance(projectMetadataArrayList);
                case 1:
                    return ModulesFragment.newInstance(modules);
                default:
                    return new Fragment();
            }
        }
    }
}