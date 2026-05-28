package com.ingpsy.designate

////////////////////////////////////////////////////////////
// global configuration file

object Config {
    const val STUDY_DAYS = 14
    const val QUESTIONNAIRE_TIMER_MS = 5 * 60 * 1000 //1 * 60 * 1000    // 5 * 60 * 1000 = 5 minutes in ms
    const val QUESTIONNAIRES_TODAY_MAX = 5

    const val MONITORING_DELAY_SEC = 5L             // checks Social Media usage every x seconds
    const val NOTIFICATION_DELAY_COUNTER = 12         // notification delay = MONITORING_DELAY_SEC * NOTIFICATION_DELAY_COUNTER
                                                      // e.g. 5 * 12 = 60sec
    const val APP_PREFS_NAME = "designate_prefs"
    const val QUESTIONNAIRE_PREFS_NAME = "questionnaire_prefs"

    const val KEY_FIRST_START = "first_start_done"     // show introduction dialogs at first start
    const val KEY_STUDY_COMPLETE = "study_complete"     // show deinstall app dialog
    const val KEY_USER_ID = "user_id"
    const val KEY_SERVICE_CONFIRMATION = "service_confirmation"

}