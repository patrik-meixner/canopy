package com.canopy.toolwindow

/**
 * The four things the review tab can be, one at a time.
 *
 * They used to be a card stack with a loading overlay painted across it, so a session whose scan
 * had not finished showed "No session selected" with "Collecting changes" written through it.
 */
enum class ReviewState { NoSession, Collecting, NothingOutstanding, Changes }
