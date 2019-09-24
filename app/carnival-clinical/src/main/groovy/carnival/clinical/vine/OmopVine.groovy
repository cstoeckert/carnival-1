package carnival.clinical.vine



import org.slf4j.Logger
import org.slf4j.LoggerFactory

import groovy.sql.*

import carnival.core.config.RelationalDatabaseConfig

import carnival.util.GenericDataTable
import carnival.core.vine.GenericDataTableVineMethod
import carnival.core.vine.CachingVine
import carnival.core.vine.RelationalVinePostgres

import carnival.util.KeyType

import java.security.MessageDigest

/**
 * Vine is the superclass of objects that interact read and write data to
 * data sources.
 *
 */
class OmopVine extends RelationalVinePostgres implements CachingVine {

	///////////////////////////////////////////////////////////////////////////
	// STATIC
	///////////////////////////////////////////////////////////////////////////
	static Logger sqllog = LoggerFactory.getLogger('sql')
	static Logger elog = LoggerFactory.getLogger('db-entity-report')
	static Logger log = LoggerFactory.getLogger('carnival')

	static MessageDigest MD5 = MessageDigest.getInstance("MD5")
	static String dbName = ""

	/** */
	static public OmopVine createFromDatabaseConfigFile(String filename, String dbName) {
		def db = RelationalDatabaseConfig.getDatabaseConfigFromFile(filename, "omop")
		this.dbName = dbName
		assert (this.dbName)
		assert (this.dbName != "")
		return new OmopVine(db)
	}

	///////////////////////////////////////////////////////////////////////////
	// CONSTRUCTORS
	///////////////////////////////////////////////////////////////////////////
	public OmopVine(RelationalDatabaseConfig rdbConfig) {
		super(rdbConfig)
	}

	///////////////////////////////////////////////////////////////////////////
	//
	// VINE METHODS
	//
	///////////////////////////////////////////////////////////////////////////

	/** */
	static class GetOmopPatientDemographicData implements GenericDataTableVineMethod {

		def sqlQuery = """

						select 

						"""+dbName+""".person.person_id,
						"""+dbName+""".person.person_source_value, 
						"""+dbName+""".person.birth_datetime,
						"""+dbName+""".person.race_concept_id,
						"""+dbName+""".person.gender_concept_id,
						gender_concept.concept_name as gender_concept_name,
						race_concept.concept_name as race_concept_name

						from """+dbName+""".person

						inner join """+dbName+""".concept gender_concept
						on """+dbName+""".person.gender_concept_id =
						gender_concept.concept_id

						inner join """+dbName+""".concept race_concept
						on """+dbName+""".person.race_concept_id =
						race_concept.concept_id

						"""

		/** validate arguments */
	    void validateArgs(Map args = [:]) {
	        assert (args.reapAllData || args.limit)
	        if (args.limit)
	        {
	        	assert (!args.reapAllData || args.reapAllData == false)
	        	assert (args.limit.toString().isInteger())
	        }
	        if (args.reapAllData)
	        {
	        	assert (args.reapAllData == true || (args.reapAllData == false && args.limit))
	        }
	    }

        GenericDataTable.MetaData meta(Map args = [:]) {
            validateArgs(args)

            // create a hashed string value
            def inputHash = MD5.digest(sqlQuery.bytes).encodeHex().toString()

            new GenericDataTable.MetaData(
                name:"omop-patient-demographics-${inputHash}",
                idFieldName:'person_source_value',
                idKeyType:KeyType.GENERIC_PATIENT_ID
            ) 

        }

        GenericDataTable fetch(Map args) {
            log.trace "GetRecords.fetch()"
            validateArgs(args)

            if (args.limit) sqlQuery += " LIMIT $args.limit "
            //log.trace(q)
            sqllog.info(sqlQuery)

            def gdt = createEmptyDataTable(args)
            enclosingVine.withSql { sql ->
                sql.eachRow(sqlQuery) { row ->
                    gdt.dataAdd(row)
                }
            }
            return gdt
        }
    }

	static class GetOmopHealthcareEncounterData implements GenericDataTableVineMethod {

		def sqlQuery = """
						select 
						person_id,
						visit_occurrence_id,
						visit_source_value,
						visit_start_datetime
						from """+dbName+""".visit_occurrence 
						where 
						person_id is not null
						"""

		/** validate arguments */
	    void validateArgs(Map args = [:]) {
	        assert (args.reapAllData || args.limit)
	        if (args.limit)
	        {
	        	assert (!args.reapAllData || args.reapAllData == false)
	        	assert (args.limit.toString().isInteger())
	        }
	        if (args.realAllData)
	        {
	        	assert (args.reapAllData == true || (args.reapAllData == false && args.limit))
	        }
	    }

        GenericDataTable.MetaData meta(Map args = [:]) {
            validateArgs(args)

            // create a hashed string value
            def inputHash = MD5.digest(sqlQuery.bytes).encodeHex().toString()

            new GenericDataTable.MetaData(
                name:"omop-healthcare-encounters-${inputHash}",
                idFieldName:'visit_occurrence_id',
                idKeyType:KeyType.ENCOUNTER_ID
            ) 

        }

        GenericDataTable fetch(Map args) {
            log.trace "GetRecords.fetch()"
            validateArgs(args)

            if (args.limit) sqlQuery += " LIMIT $args.limit "
            //log.trace(q)
            sqllog.info(sqlQuery)

            def gdt = createEmptyDataTable(args)
            enclosingVine.withSql { sql ->
                sql.eachRow(sqlQuery) { row ->
                    gdt.dataAdd(row)
                }
            }
            return gdt
        }
    }

    static class GetOmopDiagnosisData implements GenericDataTableVineMethod {

		def sqlQuery = """

						select 

						"""+dbName+""".condition_occurrence.visit_occurrence_id,
						"""+dbName+""".concept.concept_name as condition_concept_name,
						"""+dbName+""".concept.concept_code as condition_concept_code,
						"""+dbName+""".concept.vocabulary_id as condition_vocabulary_id

						from """+dbName+""".condition_occurrence

						inner join """+dbName+""".concept on
						"""+dbName+""".condition_occurrence.condition_source_concept_id = 
						"""+dbName+""".concept.concept_id

						where 
						
						"""+dbName+""".condition_occurrence.visit_occurrence_id is not null
						
						and 
						
						"""+dbName+""".concept.concept_code != 'No matching concept'

						"""

		/** validate arguments */
	    void validateArgs(Map args = [:]) {
	        assert (args.reapAllData || args.limit)
	        if (args.limit)
	        {
	        	assert (!args.reapAllData || args.reapAllData == false)
	        	assert (args.limit.toString().isInteger())
	        }
	        if (args.realAllData)
	        {
	        	assert (args.reapAllData == true || (args.reapAllData == false && args.limit))
	        }
	    }

        GenericDataTable.MetaData meta(Map args = [:]) {
            validateArgs(args)

            // create a hashed string value
            def inputHash = MD5.digest(sqlQuery.bytes).encodeHex().toString()

            new GenericDataTable.MetaData(
                name:"omop-diagnoses-${inputHash}",
                idFieldName:'visit_occurrence_id',
                idKeyType:KeyType.ENCOUNTER_ID
            ) 

        }

        GenericDataTable fetch(Map args) {
            log.trace "GetRecords.fetch()"
            validateArgs(args)

            if (args.limit) sqlQuery += " LIMIT $args.limit "
            //log.trace(q)
            sqllog.info(sqlQuery)

            def gdt = createEmptyDataTable(args)
            enclosingVine.withSql { sql ->
                sql.eachRow(sqlQuery) { row ->
                    gdt.dataAdd(row)
                }
            }
            return gdt
        }
    }

    static class GetOmopMedicationData implements GenericDataTableVineMethod {

		def sqlQuery = """

						select 

						"""+dbName+""".drug_exposure.visit_occurrence_id,
						"""+dbName+""".concept.concept_name as drug_concept_name,
						"""+dbName+""".concept.vocabulary_id as drug_vocabulary_id,
						"""+dbName+""".concept.concept_code as drug_concept_code,
						"""+dbName+""".drug_exposure.drug_exposure_id as drug_id

						from """+dbName+""".drug_exposure

						inner join """+dbName+""".concept on
						"""+dbName+""".drug_exposure.drug_concept_id = 
						"""+dbName+""".concept.concept_id 

						where """+dbName+""".drug_exposure.visit_occurrence_id is not null

						"""

		/** validate arguments */
	    void validateArgs(Map args = [:]) {
	        assert (args.reapAllData || args.limit)
	        if (args.limit)
	        {
	        	assert (!args.reapAllData || args.reapAllData == false)
	        	assert (args.limit.toString().isInteger())
	        }
	        if (args.realAllData)
	        {
	        	assert (args.reapAllData == true || (args.reapAllData == false && args.limit))
	        }
	    }

        GenericDataTable.MetaData meta(Map args = [:]) {
            validateArgs(args)

            // create a hashed string value
            def inputHash = MD5.digest(sqlQuery.bytes).encodeHex().toString()

            new GenericDataTable.MetaData(
                name:"omop-medications-${inputHash}",
                idFieldName:'visit_occurrence_id',
                idKeyType:KeyType.ENCOUNTER_ID
            ) 

        }

        GenericDataTable fetch(Map args) {
            log.trace "GetRecords.fetch()"
            validateArgs(args)

            if (args.limit) sqlQuery += " LIMIT $args.limit "
            //log.trace(q)
            sqllog.info(sqlQuery)

            def gdt = createEmptyDataTable(args)
            enclosingVine.withSql { sql ->
                sql.eachRow(sqlQuery) { row ->
                    gdt.dataAdd(row)
                }
            }
            return gdt
        }
    }

    static class GetOmopMeasurementData implements GenericDataTableVineMethod {

		def sqlQuery = """
						select 

						"""+dbName+""".measurement.visit_occurrence_id,
						"""+dbName+""".measurement.value_as_number,
						meas_concept.concept_name as measurement_concept_name,
						unit_concept.concept_name as unit_concept_name,
						"""+dbName+""".measurement.measurement_source_concept_id

						from """+dbName+""".measurement 
						inner join """+dbName+""".concept meas_concept on
						"""+dbName+""".measurement.measurement_source_concept_id = 
						meas_concept.concept_id  
						inner join """+dbName+""".concept unit_concept on
						"""+dbName+""".measurement.unit_concept_id =
						unit_concept.concept_id
						
						SUB_WHERE_CLAUSE

						AND
						value_as_number is not null
						AND
						"""+dbName+""".measurement.visit_occurrence_id is not null
						"""

		/** validate arguments */
	    void validateArgs(Map args = [:]) {
	        assert (args.reapAllData || args.limit)
	        assert (args.omopConceptMap)
	        assert (args.omopConceptMap.get("bmi"))
	        assert (args.omopConceptMap.get("weight"))
	        assert (args.omopConceptMap.get("height"))
	        assert (args.omopConceptMap.get("diastolicBloodPressure"))
	        assert (args.omopConceptMap.get("systolicBloodPressure"))
	        if (args.limit)
	        {
	        	assert (!args.reapAllData || args.reapAllData == false)
	        	assert (args.limit.toString().isInteger())
	        }
	        if (args.realAllData)
	        {
	        	assert (args.reapAllData == true || (args.reapAllData == false && args.limit))
	        }
	    }

        GenericDataTable.MetaData meta(Map args = [:]) {
            validateArgs(args)

            // create a hashed string value
            def inputHash = MD5.digest(sqlQuery.bytes).encodeHex().toString()

            new GenericDataTable.MetaData(
                name:"omop-measurements-${inputHash}",
                idFieldName:'visit_occurrence_id',
                idKeyType:KeyType.ENCOUNTER_ID
            ) 

        }

        GenericDataTable fetch(Map args) {
            log.trace "GetRecords.fetch()"
            validateArgs(args)

            if (args.limit) sqlQuery += " LIMIT $args.limit "

            //get omop concept IDs for bmi, height, and weight
            def omopHeightId = args.omopConceptMap.get("height")
            def omopWeightId = args.omopConceptMap.get("weight")
            def omopBmiId = args.omopConceptMap.get("diastolicBloodPressure")
            def diastolicBloodPressure = args.omopConceptMap.get("bmi")
            def systolicBloodPressure = args.omopConceptMap.get("systolicBloodPressure")

            def sqlInsert = """where measurement_source_concept_id in (
						'$omopBmiId',
						'$omopWeightId',
						'$omopHeightId',
						'$diastolicBloodPressure',
						'$systolicBloodPressure'
						)"""

            def sqlToRun = sqlQuery.replaceAll('SUB_WHERE_CLAUSE', sqlInsert)

            //log.trace(q)
            sqllog.info(sqlToRun)

            def gdt = createEmptyDataTable(args)
            enclosingVine.withSql { sql ->
                sql.eachRow(sqlToRun) { row ->
                    gdt.dataAdd(row)
                }
            }
            return gdt
        }
    }
}



