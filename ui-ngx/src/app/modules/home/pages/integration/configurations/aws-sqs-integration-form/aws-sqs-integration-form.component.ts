import { Component, OnInit, Input } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { Observable } from 'rxjs';


@Component({
  selector: 'tb-aws-sqs-integration-form',
  templateUrl: './aws-sqs-integration-form.component.html',
  styleUrls: ['./aws-sqs-integration-form.component.scss']
})
export class AwsSqsIntegrationFormComponent implements OnInit {

  @Input() isLoading$: Observable<boolean>;
  @Input() isEdit: boolean;
  @Input() form: FormGroup;


  constructor() { }

  ngOnInit(): void {
  }

}
