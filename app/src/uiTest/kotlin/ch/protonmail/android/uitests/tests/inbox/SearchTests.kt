/*
 * Copyright (c) 2020 Proton Technologies AG
 * 
 * This file is part of ProtonMail.
 * 
 * ProtonMail is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProtonMail is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProtonMail. If not, see https://www.gnu.org/licenses/.
 */
package ch.protonmail.android.uitests.tests.inbox

import ch.protonmail.android.uitests.robots.login.LoginMailRobot
import ch.protonmail.android.uitests.robots.mailbox.search.SearchRobot
import ch.protonmail.android.uitests.tests.BaseTest
import ch.protonmail.android.uitests.testsHelper.TestData
import ch.protonmail.android.uitests.testsHelper.annotations.SmokeTest
import ch.protonmail.android.uitests.testsHelper.annotations.TestId
import org.junit.experimental.categories.Category
import kotlin.test.BeforeTest
import kotlin.test.Test

class SearchTests : BaseTest() {

    private lateinit var searchRobot: SearchRobot

    private val loginRobot = LoginMailRobot()

    @BeforeTest
    override fun setUp() {
        super.setUp()
        searchRobot = loginRobot
            .loginOnePassUser()
            .skipOnboarding()
            .searchBar()
    }

    @TestId("53856")
    @Test
    fun searchFindMessage() {
        searchRobot
            .searchMessageText(TestData.searchMessageSubject)
            .verify { searchedMessageFound() }
    }

    @TestId("53857")
    @Test
    fun searchDontFindMessage() {
        searchRobot
            .searchMessageText(TestData.searchMessageSubjectNotFound)
            .verify { noSearchResults() }
    }
}
