package com.astroandroid.view;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.afollestad.materialdialogs.MaterialDialog;
import com.android.annotations.Nullable;
import com.astroandroid.MovieApplication;
import com.astroandroid.R;
import com.astroandroid.adapter.MoviesSimilarAdapter;
import com.astroandroid.api.MovieApi;
import com.astroandroid.databinding.ActivityMovieDetailBinding;
import com.astroandroid.db.modelDB.Favorites;
import com.astroandroid.model.ChannelEvent;
import com.astroandroid.model.Results;
import com.astroandroid.util.Constant;
import com.astroandroid.util.EndlessRecyclerViewScrollListener;
import com.astroandroid.viewmodel.MovieDetailActivityView;
import com.astroandroid.viewmodel.MovieDetailActivityViewModel;
import com.facebook.common.executors.UiThreadImmediateExecutorService;
import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.common.Priority;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.realm.Realm;

import static com.astroandroid.util.Utils.convertObjectToRealm;

public class MovieDetailActivity extends BaseActivity<ActivityMovieDetailBinding, MovieDetailActivityViewModel>
        implements MovieDetailActivityView {

    @Inject
    MovieApi movieApi;

    private static final String TAG = MovieDetailActivity.class.getName();
    private Results results;

    private MoviesSimilarAdapter moviesSimilarAdapter;
    private EndlessRecyclerViewScrollListener scrollListener;
    private Realm realmController;
    private Favorites favorite;
    private int idCharacter;
    private Bitmap bitmapImage;
    private DataSource<CloseableReference<CloseableImage>> dataSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MovieApplication.getAppComponent().inject(this);
        channelDetail = new MovieDetailActivityViewModel(movieApi);
        channelDetail.attach(this);

        bindView(R.layout.activity_movie_detail);

        binding.setIsLoading(true);
        binding.setIsFavorite(false);
        favorite = new Favorites();
        realmController = Realm.getDefaultInstance();

        binding.setActivity(this);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            results = (Results) bundle.getSerializable(Constant.ID_CHARACTER);
            binding.setResult(results);

            // custom fresco image loader
            ImagePipeline imagePipeline = Fresco.getImagePipeline();
            ImageRequest imageRequest = ImageRequestBuilder
                    .newBuilderWithSource(Uri.parse(results.getImageUrl()))
                    .setRequestPriority(Priority.HIGH)
                    .setLowestPermittedRequestLevel(ImageRequest.RequestLevel.FULL_FETCH)
                    .build();
            dataSource = imagePipeline.fetchDecodedImage(imageRequest, this);

            // fetching data from server
            channelDetail.fetchChannelDetail(results.channelId());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        realmController.close();
    }

    private List<ChannelEvent> resultsMaster = new ArrayList<>();
    private int curSize = 0;

    @Override
    public void load(List<ChannelEvent> results) {
        binding.setIsLoading(false);
        Favorites favorite = realmController.where(Favorites.class).equalTo("id", this.results.id()).findFirst();
        if (favorite != null) {
            binding.setIsFavorite(true);
        }
        if (moviesSimilarAdapter != null && moviesSimilarAdapter.getItemCount() > 0) {
            curSize = moviesSimilarAdapter.getItemCount();
        }
        binding.setIsLoading(false);
        if (results != null) {
            resultsMaster.addAll(results);
            if (moviesSimilarAdapter == null) {
                moviesSimilarAdapter = new MoviesSimilarAdapter();
                moviesSimilarAdapter.setResultItems(resultsMaster);
                binding.listMoviesSimilar.setAdapter(moviesSimilarAdapter);
            } else {
                moviesSimilarAdapter.notifyItemRangeInserted(curSize, resultsMaster.size() - 1);
            }
        } else {
            new MaterialDialog.Builder(this)
                    .title(R.string.error_title)
                    .content(R.string.error_message)
                    .positiveText(R.string.error_ok)
                    .show();
        }
    }

    @Override
    public void error(Throwable e) {
        Log.d(TAG, e.toString());
        binding.setIsLoading(false);
        new MaterialDialog.Builder(this)
                .title(R.string.error_title)
                .content(R.string.error_message)
                .positiveText(R.string.error_ok)
                .show();
    }

    /**
     * function save Character to local
     */
    public void favoriteMovie() {
        convertObjectToRealm(MovieDetailActivity.this, results, favorite, getBitmapImage());
        realmController.beginTransaction();
        realmController.copyToRealmOrUpdate(favorite);
        realmController.commitTransaction();
        binding.setIsFavorite(true);
    }

    private Bitmap getBitmapImage() {
        try {
            dataSource.subscribe(new BaseBitmapDataSubscriber() {
                @Override
                public void onNewResultImpl(@Nullable Bitmap bitmap) {
                    if (bitmap == null) {
                        Log.d(TAG, "Bitmap data source returned success, but bitmap null.");
                        return;
                    }

                    bitmapImage = bitmap;
                    // The bitmap provided to this method is only guaranteed to be around
                    // for the lifespan of this method. The image pipeline frees the
                    // bitmap's memory after this method has completed.
                    //
                    // This is fine when passing the bitmap to a system process as
                    // Android automatically creates a copy.
                    //
                    // If you need to keep the bitmap around, look into using a
                    // BaseDataSubscriber instead of a BaseBitmapDataSubscriber.
                }

                @Override
                public void onFailureImpl(DataSource dataSource) {
                    Log.d(TAG, dataSource.getFailureCause().getMessage());
                    // No cleanup required here
                }
            }, UiThreadImmediateExecutorService.getInstance());
        } finally {
            if (dataSource != null) {
                dataSource.close();
            }
        }
        return bitmapImage;
    }
}
