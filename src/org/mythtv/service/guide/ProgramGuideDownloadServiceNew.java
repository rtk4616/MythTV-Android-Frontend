/**
 * This file is part of MythTV Android Frontend
 *
 * MythTV Android Frontend is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MythTV Android Frontend is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MythTV Android Frontend.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This software can be found at <https://github.com/MythTV-Clients/MythTV-Android-Frontend/>
 */
package org.mythtv.service.guide;

import java.io.IOException;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mythtv.client.ui.preferences.LocationProfile;
import org.mythtv.db.dvr.ProgramGuideDaoHelper;
import org.mythtv.db.http.model.EtagInfoDelegate;
import org.mythtv.service.MythtvService;
import org.mythtv.service.util.DateUtils;
import org.mythtv.service.util.NetworkHelper;
import org.mythtv.services.api.guide.ProgramGuide;
import org.mythtv.services.api.guide.ProgramGuideWrapper;
import org.mythtv.services.api.guide.impl.GuideTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * @author Daniel Frey
 *
 */
public class ProgramGuideDownloadServiceNew extends MythtvService {

	private static final String TAG = ProgramGuideDownloadServiceNew.class.getSimpleName();
	
    public static final String ACTION_DOWNLOAD = "org.mythtv.background.programGuideDownloadNew.ACTION_DOWNLOAD";
    public static final String ACTION_PROGRESS = "org.mythtv.background.programGuideDownloadNew.ACTION_PROGRESS";
    public static final String ACTION_COMPLETE = "org.mythtv.background.programGuideDownloadNew.ACTION_COMPLETE";

    public static final String EXTRA_PROGRESS = "PROGRESS";
    public static final String EXTRA_PROGRESS_DATE = "PROGRESS_DATE";
    public static final String EXTRA_PROGRESS_ERROR = "PROGRESS_ERROR";
    public static final String EXTRA_COMPLETE = "COMPLETE";
    public static final String EXTRA_COMPLETE_UPTODATE = "COMPLETE_UPTODATE";
    public static final String EXTRA_COMPLETE_OFFLINE = "COMPLETE_OFFLINE";

	private ProgramGuideDaoHelper mProgramGuideDaoHelper = ProgramGuideDaoHelper.getInstance(); 
	
	public ProgramGuideDownloadServiceNew() {
		super( "ProgamGuideDownloadServiceNew" );
	}

	/* (non-Javadoc)
	 * @see org.mythtv.service.MythtvService#onHandleIntent(android.content.Intent)
	 */
	@Override
	protected void onHandleIntent( Intent intent ) {
		Log.v( TAG, "onHandleIntent : enter" );
		super.onHandleIntent( intent );

		boolean passed = true;
		
		LocationProfile locationProfile = mLocationProfileDaoHelper.findConnectedProfile( this );
		if( !NetworkHelper.getInstance().isMasterBackendConnected( this, locationProfile ) ) {
			Intent completeIntent = new Intent( ACTION_COMPLETE );
			completeIntent.putExtra( EXTRA_COMPLETE, "Master Backend unreachable" );
			completeIntent.putExtra( EXTRA_COMPLETE_OFFLINE, Boolean.TRUE );
			sendBroadcast( completeIntent );

			Log.d( TAG, "onHandleIntent : exit, Master Backend unreachable" );
			return;
		}

		if ( intent.getAction().equals( ACTION_DOWNLOAD ) ) {
			
			try {

				download( locationProfile );

			} catch( Exception e ) {
				Log.e( TAG, "onHandleIntent : error", e );
				
				passed = false;
			} finally {

				Intent completeIntent = new Intent( ACTION_COMPLETE );
				completeIntent.putExtra( EXTRA_COMPLETE, "Program Guide Download Service Finished" );
				completeIntent.putExtra( EXTRA_COMPLETE_UPTODATE, passed );
				sendBroadcast( completeIntent );
			
			}
			
		}
		
		Log.v( TAG, "onHandleIntent : exit" );
	}

	// internal helpers
	
	private void download( final LocationProfile locationProfile ) throws Exception {
		Log.v( TAG, "download : enter" );
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences( this );
		int downloadDays = Integer.parseInt( sp.getString( "preference_program_guide_days", "14" ) );
		Log.v( TAG, "download : downloadDays=" + downloadDays );
		
		DateTime start = new DateTime( DateTimeZone.getDefault() ).withTimeAtStartOfDay();
		DateTime end = start.plusDays( 1 );
		for( int i = 0; i < downloadDays; i++ ) {
			Log.i( TAG, "download : starting download for " + DateUtils.getDateTimeUsingLocaleFormattingPretty( start, mMainApplication.getDateFormat(), mMainApplication.getClockType() ) + ", end time=" + DateUtils.getDateTimeUsingLocaleFormattingPretty( end, mMainApplication.getDateFormat(), mMainApplication.getClockType() ) );

			EtagInfoDelegate etag = mEtagDaoHelper.findByEndpointAndDate( this, locationProfile, GuideTemplate.Endpoint.GET_PROGRAM_GUIDE.name(), start );

			ResponseEntity<ProgramGuideWrapper> responseEntity = mMythtvServiceHelper.getMythServicesApi( locationProfile ).guideOperations().getProgramGuide( start, end, 1, -1, false, etag );

			DateTime date = DateUtils.convertUtc( new DateTime( DateTimeZone.getDefault() ) );
			if( responseEntity.getStatusCode().equals( HttpStatus.OK ) ) {
				Log.i( TAG, "download : " + GuideTemplate.Endpoint.GET_PROGRAM_GUIDE.name() + " returned 200 OK" );
				ProgramGuideWrapper programGuide = responseEntity.getBody();

				if( null != programGuide ) {

					if( null != programGuide.getProgramGuide() ) {
						process( programGuide.getProgramGuide(), locationProfile );
					}

				}

				if( null != etag.getValue() ) {
					Log.i( TAG, "download : saving etag: " + etag.getValue() );

					etag.setEndpoint( GuideTemplate.Endpoint.GET_PROGRAM_GUIDE.name() );
					etag.setDate( start );
					etag.setMasterHostname( locationProfile.getHostname() );
					etag.setLastModified( date );
					mEtagDaoHelper.save( this, locationProfile, etag );
				}

				if( responseEntity.getStatusCode().equals( HttpStatus.NOT_MODIFIED ) ) {
					Log.i( TAG, "download : " + GuideTemplate.Endpoint.GET_PROGRAM_GUIDE.name() + " returned 304 Not Modified" );

					if( null != etag.getValue() ) {
						Log.i( TAG, "download : saving etag: " + etag.getValue() );

						etag.setLastModified( date );
						mEtagDaoHelper.save( this, locationProfile, etag );
					}

				}

			}
			
			start = start.plusDays( 1 );
			end = end.plusDays( 1 );
		}
		
		Log.v( TAG, "download : exit" );
	}

	private void process( ProgramGuide programGuide, final LocationProfile locationProfile ) throws JsonGenerationException, JsonMappingException, IOException, RemoteException, OperationApplicationException {
		Log.v( TAG, "process : enter" );

		Log.v( TAG, "process : saving program guide for host [" + locationProfile.getHostname() + ":" + locationProfile.getUrl() + "]" );
		
		int programsAdded = mProgramGuideDaoHelper.loadProgramGuide( this, locationProfile, programGuide.getChannels() );
		Log.v( TAG, "process : programsAdded=" + programsAdded );
	
		Log.v( TAG, "process : exit" );
	}

}
