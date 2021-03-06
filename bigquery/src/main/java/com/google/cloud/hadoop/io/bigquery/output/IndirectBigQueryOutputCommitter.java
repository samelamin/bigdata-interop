package com.google.cloud.hadoop.io.bigquery.output;

import com.google.api.services.bigquery.model.TableReference;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.cloud.hadoop.io.bigquery.BigQueryFileFormat;
import java.io.IOException;
import java.util.List;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.JobStatus.State;
import org.apache.hadoop.mapreduce.OutputCommitter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

/**
 * This class acts as a wrapper which delegates calls to another OutputCommitter whose
 * responsibility is to generate files in the defined output path. This class will ensure that those
 * file are imported into BigQuery and cleaned up locally.
 */
@InterfaceStability.Unstable
public class IndirectBigQueryOutputCommitter extends ForwardingBigQueryFileOutputCommitter {

  /**
   * This class acts as a wrapper which delegates calls to another OutputCommitter whose
   * responsibility is to generate files in the defined output path. This class will ensure that
   * those file are imported into BigQuery and cleaned up locally.
   *
   * @param context the context of the task.
   * @param delegate the OutputCommitter that this will delegate functionality to.
   * @throws IOException if there's an exception while validating the output path or getting the
   *     BigQueryHelper.
   */
  public IndirectBigQueryOutputCommitter(TaskAttemptContext context, OutputCommitter delegate)
      throws IOException {
    super(context, delegate);
  }

  /**
   * Runs an import job on BigQuery for the data in the output path in addition to calling the
   * delegate's commitJob.
   */
  @Override
  public void commitJob(JobContext context) throws IOException {
    super.commitJob(context);

    // Get the destination configuration information.
    Configuration conf = context.getConfiguration();
    TableReference destTable = BigQueryOutputConfiguration.getTableReference(conf);
    String destProjectId = BigQueryOutputConfiguration.getProjectId(conf);
    String writeDisposition = BigQueryOutputConfiguration.getWriteDisposition(conf);
    TableSchema destSchema = BigQueryOutputConfiguration.getTableSchema(conf);
    BigQueryFileFormat outputFileFormat = BigQueryOutputConfiguration.getFileFormat(conf);
    List<String> sourceUris = getOutputFileURIs();

    try {
      getBigQueryHelper()
          .importFromGcs(
              destProjectId,
              destTable,
              destSchema,
              outputFileFormat,
              writeDisposition,
              sourceUris,
              true);
    } catch (InterruptedException e) {
      throw new IOException("Failed to import GCS into BigQuery", e);
    }

    cleanup(context);
  }

  /**
   * Performs a cleanup of the output path in addition to delegating the call to the wrapped
   * OutputCommitter.
   */
  @Override
  public void abortJob(JobContext context, State state) throws IOException {
    super.abortJob(context, state);
    cleanup(context);
  }
}
