/**
 * 
 */
package org.mythtv.db.dvr.programGroup;

import org.mythtv.provider.MythtvProvider;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * @author Daniel Frey
 *
 */
public class ProgramGroupConstants implements BaseColumns {

	public static final String TABLE_NAME = "program_group";

	public static final Uri CONTENT_URI = Uri.parse( "content://" + MythtvProvider.AUTHORITY + "/" + TABLE_NAME );

	public static final String INSERT_ROW, UPDATE_ROW;

	public static final String FIELD_ID_DATA_TYPE = "INTEGER";
	public static final String FIELD_ID_PRIMARY_KEY = "PRIMARY KEY AUTOINCREMENT";

	public static final String FIELD_PROGRAM_GROUP = "PROGRAM_GROUP";
	public static final String FIELD_PROGRAM_GROUP_DATA_TYPE = "TEXT";
	
	public static final String FIELD_TITLE = "TITLE";
	public static final String FIELD_TITLE_DATA_TYPE = "TEXT";
	
	public static final String FIELD_CATEGORY = "CATEGORY";
	public static final String FIELD_CATEGORY_DATA_TYPE = "TEXT";
	
	public static final String FIELD_INETREF = "INETREF";
	public static final String FIELD_INETREF_DATA_TYPE = "TEXT";
	
	static {
		StringBuilder insert = new StringBuilder();
		insert.append( FIELD_PROGRAM_GROUP ).append( "," );
		insert.append( FIELD_TITLE ).append( "," );
		insert.append( FIELD_CATEGORY ).append( "," );
		insert.append( FIELD_INETREF );
		
		StringBuilder values = new StringBuilder();
		values.append( " ) " );
		values.append( "VALUES( ?,?,?,? )" );
		
		StringBuilder insertProgramGroup = new StringBuilder();
		insertProgramGroup.append( "INSERT INTO " ).append( TABLE_NAME ).append( " ( " );
		insertProgramGroup.append( insert.toString() );
		insertProgramGroup.append( values.toString() );
		INSERT_ROW = insertProgramGroup.toString();
		
		StringBuilder update = new StringBuilder();
		update.append( FIELD_PROGRAM_GROUP ).append( " = ?, " );
		update.append( FIELD_TITLE ).append( " = ?, " );
		update.append( FIELD_CATEGORY ).append( " = ?, " );
		update.append( FIELD_INETREF ).append( " = ?" );
		update.append( " WHERE " );
		update.append( _ID ).append( " = ?" );
		
		StringBuilder updateProgramGroup = new StringBuilder();
		updateProgramGroup.append( "UPDATE " ).append( TABLE_NAME );
		updateProgramGroup.append( " SET " );
		updateProgramGroup.append( update.toString() );
		UPDATE_ROW = updateProgramGroup.toString();
		
	}
	
}
